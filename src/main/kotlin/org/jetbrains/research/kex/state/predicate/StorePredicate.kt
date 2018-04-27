package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term

class StorePredicate(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State()) : Predicate(type, arrayOf(lhv, rhv)) {
    fun getLhv() = operands[0]
    fun getStoreVal() = operands[1]

    override fun print() = "${getLhv()} = ${getStoreVal()}"
}