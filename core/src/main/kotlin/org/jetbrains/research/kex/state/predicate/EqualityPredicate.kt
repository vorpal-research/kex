package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

class EqualityPredicate(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, listOf(lhv, rhv)) {
    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print() = "${getLhv()} = ${getRhv()}"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val lhv = t.transform(getLhv())
        val rhv = t.transform(getRhv())
        return when {
            lhv == getLhv() && rhv == getRhv() -> this
            else -> t.pf.getEquality(lhv, rhv, type)
        }
    }
}