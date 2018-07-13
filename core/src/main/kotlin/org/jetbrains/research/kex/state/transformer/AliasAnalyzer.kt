package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.collections.DisjointSet
import org.jetbrains.research.kex.collections.Subset
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.GraphView
import org.jetbrains.research.kfg.util.viewCfg

typealias Token = Subset<Term?>?

class AliasAnalyzer : Transformer<AliasAnalyzer> {
    val relations = DisjointSet<Term?>()
    val pointsTo = mutableMapOf<Token, Token>()
    val mapping = mutableMapOf<Term, Token>()
    val nonaliased = mutableSetOf<Term>()
    val spaces = mutableMapOf<Type, Token>()
    val nonFreeTerms = mutableSetOf<Term>()

    fun pointsTo(token: Token) = pointsTo.getOrPut(token) { null }
    fun spaces(type: Type) = spaces.getOrPut(type) { null }

    private fun quasi(): Token = relations.emplace(null)
    private fun join(lhv: Token, rhv: Token): Token = when {
        (lhv != null) and (rhv != null) ->
            if (lhv == rhv) lhv
            else {
                val lpts = relations.findUnsafe(pointsTo(lhv))
                val rpts = relations.findUnsafe(pointsTo(rhv))
                val res = relations.joinUnsafe(lhv, rhv)
                val newpts = join(lpts, rpts)
                pointsTo[lhv] = newpts
                pointsTo[rhv] = newpts
                pointsTo[res] = newpts
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

    fun get(term: Term): Token = when {
        mapping.contains(term) -> mapping[term]?.getRoot()
        else -> {
            val token = relations.emplace(term)
            mapping[term] = token

            if (term is ValueTerm && term.name == "this") {
                nonFreeTerms.add(term)
                nonaliased.add(term)
            }
            if (!nonFreeTerms.contains(term)) {
                join(spaces(term.type), token)
            }
            token
        }
    }

    override fun transformArrayLoadTerm(term: ArrayLoadTerm): Term {
        val ts = get(term)
        val loads = get(term.getArrayRef())
        val res = join(pointsTo(loads), pointsTo(ts))
        pointsTo[loads] = res
        pointsTo[ts] = res
        return term
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val ts = get(term)
        val lts = get(term.getLhv())
        val rts = get(term.getRhv())
        val res1 = join(pointsTo(ts), pointsTo(lts))
        pointsTo[ts] = res1
        pointsTo[lts] = res1
        val res2 = join(pointsTo(ts), pointsTo(rts))
        pointsTo[ts] = res2
        pointsTo[lts] = res2
        pointsTo[rts] = res2
        return term
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val ts = get(term)
        val operand = get(term.getOperand())
        val res = join(pointsTo(ts), pointsTo(operand))
        pointsTo[ts] = res
        pointsTo[operand] = res
        return term
    }

    override fun transformFieldLoadTerm(term: FieldLoadTerm): Term {
        val ts = get(term)

        val lhs = get(term.getField())
        val rs = join(pointsTo(lhs), pointsTo(ts))
        pointsTo[lhs] = rs
        pointsTo[ts] = rs

        val res = join(spaces(term.type), pointsTo(ts))
        pointsTo[ts] = res
        return term
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        if (predicate.type is PredicateType.State) nonFreeTerms.add(predicate.getLhv())

        val ls = get(predicate.getLhv())
        val rs = get(predicate.getRhv())

        val res = join(pointsTo(ls), pointsTo(rs))
        pointsTo[ls] = res
        pointsTo[rs] = res
        return predicate
    }

    override fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate {
        nonaliased.add(predicate.getLhv())
        nonFreeTerms.add(predicate.getLhv())

        val ls = get(predicate.getLhv())
        pointsTo[ls] = quasi()
        return predicate
    }

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        nonaliased.add(predicate.getLhv())
        nonFreeTerms.add(predicate.getLhv())

        val ls = get(predicate.getLhv())
        pointsTo[ls] = quasi()
        return predicate
    }

    override fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate {
        val ls = get(predicate.getArrayRef())
        val rs = get(predicate.getValue())
        val res = join(pointsTo(ls), rs)
        pointsTo[ls] = res
        return predicate
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        val ls = get(predicate.getField())
        val rs = get(predicate.getValue())
        val res = join(pointsTo(ls), rs)
        pointsTo[ls] = res
        return predicate
    }


    fun mayAlias(lhv: Term, rhv: Term): Boolean {
        if (lhv === rhv) return true

        if (nonaliased.contains(lhv) && nonaliased.contains(rhv)) return false

        val ls = pointsTo(get(lhv))
        val rs = pointsTo(get(rhv))
        return when {
            ls == null -> false
            rs == null -> false
            else -> ls.getRoot() == rs.getRoot()
        }
    }

    fun getDereferenced(term: Term) = relations.findUnsafe(pointsTo(get(term)))

    fun asGraph(): List<GraphView> {
        val rootNode = GraphView("root", "root")
        val reverse = mutableMapOf<Token, GraphView>()
        val data = mutableMapOf<Token, MutableSet<Term>>()
        for ((term, token) in mapping) {
            val root = relations.findUnsafe(token)
            data.getOrPut(root) { mutableSetOf() }.add(term)
        }
        for ((token, dt) in data) {
            reverse[token] = GraphView(token.toString(), dt.fold(StringBuilder()) { sb, term -> sb.appendln(term.print()) }.toString())
        }
        for ((f, t) in pointsTo) {
            val from = relations.findUnsafe(f)
            val to = relations.findUnsafe(t)
            val fromNode = reverse.getOrPut(from) { GraphView(from.toString(), from.toString()) }
            val toNode = reverse.getOrPut(to) { GraphView(to.toString(), to.toString()) }
            fromNode.successors.add(toNode)
        }
        val values = reverse.values.map { GraphView(it.name, it.label, it.successors.toSet().toMutableList()) }.toMutableSet()
        values.filter { it.successors.isEmpty() }.forEach { it.successors.add(rootNode) }
        values.add(rootNode)
        return values.toList()
    }

    fun viewGraph() = viewCfg("SteensgaardAA", asGraph())
}