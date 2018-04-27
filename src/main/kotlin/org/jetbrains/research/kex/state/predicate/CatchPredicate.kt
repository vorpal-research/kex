package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term

class CatchPredicate(throwable: Term, type: PredicateType = PredicateType.State()) : Predicate(type, arrayOf(throwable)) {
    fun getThrowable() = operands[0]

    override fun print() = "catch ${getThrowable()}"
}