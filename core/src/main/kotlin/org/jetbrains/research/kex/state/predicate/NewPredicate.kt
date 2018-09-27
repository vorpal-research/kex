package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

class NewPredicate(lhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) :
        Predicate(type, location, listOf(lhv)) {

    val lhv: Term
        get() = operands[0]

    override fun print() = "$lhv = new ${lhv.type}"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tlhv = t.transform(lhv)
        return when (tlhv) {
            lhv -> this
            else -> t.pf.getNew(lhv, type)
        }
    }
}