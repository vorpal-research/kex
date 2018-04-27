package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term

class EqualityPredicate(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State()) : Predicate(type, arrayOf(lhv, rhv)) {
    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print() = "${getLhv()} = ${getRhv()}"
}