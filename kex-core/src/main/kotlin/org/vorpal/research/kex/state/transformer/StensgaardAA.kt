package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.predicate.ArrayInitializerPredicate
import org.vorpal.research.kex.state.predicate.ArrayStorePredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.FieldInitializerPredicate
import org.vorpal.research.kex.state.predicate.FieldStorePredicate
import org.vorpal.research.kex.state.predicate.NewArrayInitializerPredicate
import org.vorpal.research.kex.state.predicate.NewArrayPredicate
import org.vorpal.research.kex.state.predicate.NewInitializerPredicate
import org.vorpal.research.kex.state.predicate.NewPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.term.ArrayContainsTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.CastTerm
import org.vorpal.research.kex.state.term.CmpTerm
import org.vorpal.research.kex.state.term.ConcatTerm
import org.vorpal.research.kex.state.term.EqualsTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.isNamed
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.DisjointSet
import org.vorpal.research.kthelper.collection.Subset
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.Viewable
import org.vorpal.research.kthelper.logging.log

private val dot by lazy {
    kexConfig.getStringValue("view", "dot") ?: unreachable { log.error("Could not find dot") }
}

private val viewer by lazy {
    kexConfig.getStringValue("view", "viewer") ?: unreachable { log.error("Could not find viewer") }
}

typealias Token = Subset<Term>

interface AliasAnalysis {
    fun mayAlias(lhv: Term, rhv: Term): Boolean
}

class StensgaardAA : Transformer<StensgaardAA>, AliasAnalysis, Viewable {
    private val relations = DisjointSet<Term>()
    private val pointsTo = hashMapOf<Token?, Token?>()
    private val mapping = hashMapOf<Term, Token?>()
    private val nonAliased = hashSetOf<Term>()
    private val spaces = hashMapOf<KexType, Token?>()
    private val nonFreeTerms = hashSetOf<Term>()

    private fun pointsTo(token: Token?) = pointsTo.getOrPut(token) { null }
    private fun spaces(type: KexType) = spaces.getOrPut(type) { null }

    private fun quasi(): Token = relations.emplace(null)
    private fun join(lhv: Token?, rhv: Token?): Token? = when {
        lhv != null && rhv != null ->
            if (lhv == rhv) lhv
            else {
                val lPts = relations.findUnsafe(pointsTo(lhv))
                val rPts = relations.findUnsafe(pointsTo(rhv))
                val res = relations.joinUnsafe(lhv, rhv)
                val newPts = join(lPts, rPts)
                pointsTo[lhv] = newPts
                pointsTo[rhv] = newPts
                pointsTo[res] = newPts
                res
            }
        lhv != null -> {
            val res = relations.findUnsafe(lhv)
            val pres = relations.findUnsafe(pointsTo(lhv))
            pointsTo[res] = pres
            pointsTo[lhv] = pres
            res
        }
        rhv != null -> {
            val res = relations.findUnsafe(rhv)
            val pres = relations.findUnsafe(pointsTo(rhv))
            pointsTo[res] = pres
            pointsTo[rhv] = pres
            res
        }
        else -> quasi()
    }

    fun get(term: Term): Token? = when (term) {
        in mapping -> mapping[term]!!.getRoot()
        else -> {
            var token: Token? = relations.emplace(term)

            if (term !in nonFreeTerms && term.isNamed) {
                val result = join(spaces(term.type), token)
                spaces[term.type] = result
                token = result


                val pt = join(pointsTo(token), null)
                pointsTo[token] = pt
            }
            mapping[term] = token
            token
        }
    }

    override fun transformArrayContainsTerm(term: ArrayContainsTerm): Term {
        val ts = get(term.value)
        val loads = get(term.array)
        val res = join(pointsTo(loads), pointsTo(ts))
        pointsTo[loads] = res
        pointsTo[ts] = res
        return term
    }

    override fun transformArrayLoadTerm(term: ArrayLoadTerm): Term {
        val ts = get(term)
        val loads = get(term.arrayRef)
        val res = join(pointsTo(loads), pointsTo(ts))
        pointsTo[loads] = res
        pointsTo[ts] = res
        return term
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val ts = get(term)
        val lts = get(term.lhv)
        val rts = get(term.rhv)
        val res1 = join(pointsTo(ts), pointsTo(lts))
        pointsTo[ts] = res1
        pointsTo[lts] = res1
        val res2 = join(pointsTo(ts), pointsTo(rts))
        pointsTo[ts] = res2
        pointsTo[lts] = res2
        pointsTo[rts] = res2
        return term
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val ls = get(term.lhv)
        val rs = get(term.rhv)

        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return term
    }

    override fun transformConcatTerm(term: ConcatTerm): Term {
        val ls = get(term.lhv)
        val rs = get(term.rhv)

        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return term
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val ts = get(term)
        val operand = get(term.operand)
        val res = join(pointsTo(ts), pointsTo(operand))
        pointsTo[ts] = res
        pointsTo[operand] = res
        return term
    }

    override fun transformEqualsTerm(term: EqualsTerm): Term {
        val ls = get(term.lhv)
        val rs = get(term.rhv)

        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return term
    }

    override fun transformFieldLoadTerm(term: FieldLoadTerm): Term {
        val ts = get(term)

        val lhs = get(term.field)
        val rs = join(pointsTo(lhs), pointsTo(ts))
        pointsTo[lhs] = rs
        pointsTo[ts] = rs

        val res = join(spaces(term.type), pointsTo(ts))
        pointsTo[ts] = res
        return term
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        if (predicate.type is PredicateType.State) nonFreeTerms.add(predicate.lhv)

        val ls = get(predicate.lhv)
        val rs = get(predicate.rhv)

        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return predicate
    }

    override fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate {
        nonAliased.add(predicate.lhv)
        nonFreeTerms.add(predicate.lhv)

        val ls = get(predicate.lhv)
        pointsTo[ls] = quasi()
        return predicate
    }

    override fun transformNewArrayInitializerPredicate(predicate: NewArrayInitializerPredicate): Predicate {
        nonAliased.add(predicate.lhv)
        nonFreeTerms.add(predicate.lhv)

        val ls = get(predicate.lhv)
        pointsTo[ls] = quasi()
        return predicate
    }

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        nonAliased.add(predicate.lhv)
        nonFreeTerms.add(predicate.lhv)

        val ls = get(predicate.lhv)
        pointsTo[ls] = quasi()
        return predicate
    }

    override fun transformNewInitializerPredicate(predicate: NewInitializerPredicate): Predicate {
        nonAliased.add(predicate.lhv)
        nonFreeTerms.add(predicate.lhv)

        val ls = get(predicate.lhv)
        pointsTo[ls] = quasi()
        return predicate
    }

    override fun transformArrayInitializerPredicate(predicate: ArrayInitializerPredicate): Predicate {
        val ls = get(predicate.arrayRef)
        val rs = get(predicate.value)
        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return predicate
    }

    override fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate {
        val ls = get(predicate.arrayRef)
        val rs = get(predicate.value)
        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return predicate
    }

    override fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate {
        val ls = get(predicate.field)
        val rs = get(predicate.value)
        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return predicate
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        val ls = get(predicate.field)
        val rs = get(predicate.value)
        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return predicate
    }


    override fun mayAlias(lhv: Term, rhv: Term): Boolean {
        if (lhv === rhv) return true

        if (nonAliased.contains(lhv) && nonAliased.contains(rhv)) return false

        val ls = pointsTo(get(lhv))
        val rs = pointsTo(get(rhv))
        return when {
            ls == null -> false
            rs == null -> false
            else -> ls.getRoot() == rs.getRoot()
        }
    }

    fun getDereferenced(term: Term): Token? = relations.findUnsafe(pointsTo(get(term)))

    override val graphView: List<GraphView>
        get() {
            val rootNode = GraphView("root", "root")
            val reverse = mutableMapOf<Token?, GraphView>()
            val data = mutableMapOf<Token?, MutableSet<Term>>()
            for ((term, token) in mapping) {
                val root = relations.findUnsafe(token)
                data.getOrPut(root) { mutableSetOf() }.add(term)
            }
            for ((token, dt) in data) {
                reverse[token] = GraphView(
                    token.toString(),
                    dt.fold(StringBuilder()) { sb, term -> sb.appendLine(term) }.toString()
                )
            }
            for ((f, t) in pointsTo) {
                val from = relations.findUnsafe(f)
                val to = relations.findUnsafe(t)
                val fromNode = reverse.getOrPut(from) { GraphView(from.toString(), from.toString()) }
                val toNode = reverse.getOrPut(to) { GraphView(to.toString(), to.toString()) }
                fromNode.addSuccessor(toNode)
            }
            val values = reverse.values.map { graphView ->
                val view = GraphView(graphView.name, graphView.label)
                graphView.successors.toSet().forEach { view.addSuccessor(it.first, it.second) }
                view
            }.toMutableSet()
            values.filter { it.successors.isEmpty() }.forEach { it.addSuccessor(rootNode) }
            values.add(rootNode)
            return values.toList()
        }

    @Suppress("unused")
    fun view() = view("StensgaardAA", dot, viewer)
}
