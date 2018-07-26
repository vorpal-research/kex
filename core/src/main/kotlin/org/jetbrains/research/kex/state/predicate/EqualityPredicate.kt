package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

class EqualityPredicate(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, listOf(lhv, rhv)) {

    val lhv: Term
        get() = operands[0]

    val rhv: Term
        get() = operands[1]

    override fun print() = "$lhv = $rhv"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tlhv = t.transform(lhv)
        val trhv = t.transform(rhv)
        return when {
            tlhv == lhv && trhv == rhv -> this
            else -> t.pf.getEquality(tlhv, trhv, type)
        }
    }
}