package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term

class NewPredicate(lhv: Term, type: PredicateType = PredicateType.State()) : Predicate(type, arrayOf(lhv)) {
    fun getLhv() = operands[0]

    override fun print() = "${getLhv()} = new ${getLhv().type}"
}