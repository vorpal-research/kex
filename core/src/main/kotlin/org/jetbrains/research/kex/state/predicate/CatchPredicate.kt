package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

class CatchPredicate(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) :
        Predicate(type, location, listOf(throwable)) {
    fun getThrowable() = operands[0]

    override fun print() = "catch ${getThrowable()}"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val throwable = t.transform(getThrowable())
        return when {
            throwable == getThrowable() -> this
            else -> t.pf.getCatch(getThrowable(), type)
        }
    }
}