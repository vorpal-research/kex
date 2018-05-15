package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer

class NewPredicate(lhv: Term, type: PredicateType = PredicateType.State()) : Predicate(type, arrayOf(lhv)) {
    fun getLhv() = operands[0]

    override fun print() = "${getLhv()} = new ${getLhv().type}"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val lhv = t.transform(getLhv())
        return when {
            lhv == getLhv() -> this
            else -> t.pf.getNew(lhv, type)
        }
    }
}