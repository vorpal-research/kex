package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.algorithm.GraphView
import org.jetbrains.research.kex.algorithm.viewCfg
import org.jetbrains.research.kex.collections.DisjointSet
import org.jetbrains.research.kex.collections.Mutable
import org.jetbrains.research.kex.collections.Subset
import org.jetbrains.research.kex.collections.wrap
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.type.Type

typealias Token = Mutable<Subset<Term?>>

class AliasAnalyzer : Transformer<AliasAnalyzer> {
    val relations = DisjointSet<Term?>()
    val pointsTo = mutableMapOf<Token, Token>()
    val mapping = mutableMapOf<Term, Token>()
    val nonaliased = mutableSetOf<Term>()
    val spaces = mutableMapOf<Type, Token>()
    val nonFreeTerms = mutableSetOf<Term>()

    fun emplace(term: Term?) = relations.emplace(term).wrap()
    fun find(token: Token) = relations.findUnsafe(token.data).wrap()
    fun join_relations(lhv: Token, rhv: Token) = relations.join(lhv.unwrap(), rhv.unwrap()).wrap()
    fun pointsTo(token: Token) = pointsTo.getOrPut(token) { Mutable(null) }
    fun spaces(type: Type) = spaces.getOrPut(type) { Mutable(null) }

    private fun quasi(): Token = emplace(null)
    private fun join(lhv: Token, rhv: Token): Token {
        val result = when {
            (lhv.valid()) and (rhv.valid()) ->
                if (lhv == rhv) lhv
                else {
                    val lpts = find(pointsTo(lhv))
                    val rpts = find(pointsTo(rhv))
                    val res = join_relations(lhv, rhv)
                    val newpts = join(lpts, rpts)
                    pointsTo[lhv] = newpts
                    pointsTo[rhv] = newpts
                    pointsTo[res] = newpts
                    res
                }
            lhv.valid() -> {
                val res = find(lhv)
                val pres = find(pointsTo(lhv))
                pointsTo[res] = pres
                pointsTo[lhv] = pres
                res
            }
            rhv.valid() -> {
                val res = find(rhv)
                val pres = find(pointsTo(rhv))
                pointsTo[res] = pres
                pointsTo[rhv] = pres
                res
            }
            else -> quasi()
        }
        return lhv `=` rhv `=` result
    }

    fun get(term: Term): Token = when {
        mapping.contains(term) -> mapping.getValue(term).unwrap().getRoot().wrap()
        else -> {
            val token = emplace(term)
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
        join(pointsTo(loads), ts)
        return term
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val ts = get(term)
        val lts = get(term.getLhv())
        val rts = get(term.getRhv())
        join(pointsTo(ts), pointsTo(lts))
        join(pointsTo(ts), pointsTo(rts))
        return term
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val ts = get(term)
        val operand = get(term.getOperand())
        join(pointsTo(ts), pointsTo(operand))
        return term
    }

    override fun transformFieldLoadTerm(term: FieldLoadTerm): Term {
        if (term.isStatic) {
            nonFreeTerms.add(term)
        }
        val ts = get(term)
        if (!term.isStatic) {
            val lhs = get(term.getObjectRef()!!)
            join(pointsTo(lhs), ts)
        }
        join(spaces(term.type), pointsTo(ts))
        return term
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        if (predicate.type is PredicateType.State) nonFreeTerms.add(predicate.getLhv())

        val ls = get(predicate.getLhv())
        val rs = get(predicate.getRhv())

        join(pointsTo(ls), pointsTo(rs))
        return predicate
    }

    override fun transformMultiNewArrayPredicate(predicate: MultiNewArrayPredicate): Predicate {
        nonaliased.add(predicate.getLhv())
        nonFreeTerms.add(predicate.getLhv())

        val ls = get(predicate.getLhv())
        pointsTo[ls] = quasi()
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

    override fun transformStorePredicate(predicate: StorePredicate): Predicate {
        val ls = get(predicate.getLhv())
        val rs = get(predicate.getStoreVal())
        join(pointsTo(ls), rs)
        return predicate
    }

    fun mayAlias(lhv: Term, rhv: Term): Boolean {
        if (lhv === rhv) return true

        if (nonaliased.contains(lhv) && nonaliased.contains(rhv)) return false

        val ls = pointsTo(get(lhv))
        val rs = pointsTo(get(rhv))
        return if (ls.valid() and rs.valid()) ls.unwrap().getRoot() == rs.unwrap().getRoot() else false
    }

    fun asGraph(): List<GraphView> {
        val rootNode = GraphView("root", "root")
        val reverse = mutableMapOf<Subset<Term?>?, GraphView>()
        val data = mutableMapOf<Subset<Term?>, MutableSet<Term>>()
        for ((term, token) in mapping) {
            val root = find(token)
            data.getOrPut(root.unwrap()) { mutableSetOf() }.add(term)
        }
        for ((token, dt) in data) {
            reverse[token] = GraphView(token.data.toString(), dt.fold(StringBuilder()) { sb, term -> sb.appendln(term.print()) }.toString())
        }
        for ((f, t) in pointsTo) {
            val from = find(f)
            val to = find(t)
            val fromNode = reverse.getOrPut(from.data) { GraphView(from.data.toString(), from.data.toString()) }
            val toNode = reverse.getOrPut(to.data) { GraphView(to.data.toString(), to.data.toString()) }
            fromNode.successors.add(toNode)
        }
        val values = reverse.values.map { GraphView(it.name, it.label, it.successors.toSet().toMutableList()) }.toMutableSet()
        values.filter { it.successors.isEmpty() }.forEach { it.successors.add(rootNode) }
        values.add(rootNode)
        return values.toList()
    }

    fun viewGraph() = viewCfg("SteensgaardAA", asGraph())
}