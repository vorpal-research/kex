package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

class NewPredicate(lhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) :
        Predicate(type, location, listOf(lhv)) {
    fun getLhv() = operands[0]

    override fun print() = "${getLhv()} = new ${getLhv().type}"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val lhv = t.transform(getLhv())
        return when {
            lhv == getLhv() -> this
            else -> t.pf.getNew(lhv, type)
        }
    }
}