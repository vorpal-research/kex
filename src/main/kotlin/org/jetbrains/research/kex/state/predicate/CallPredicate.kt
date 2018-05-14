package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term

class CallPredicate : Predicate {
    val hasLhv: Boolean

    constructor(callTerm: Term, type: PredicateType = PredicateType.State())
            : super(type, arrayOf(callTerm)) {
        hasLhv = false
    }

    constructor(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State())
            : super(type, arrayOf(lhv, callTerm)) {
        hasLhv = true
    }

    fun getLhv() = if (hasLhv) operands[0] else null
    fun getCall() = if (hasLhv) operands[1] else operands[0]

    override fun print(): String {
        val sb = StringBuilder()
        if (hasLhv) sb.append("${getLhv()} = ")
        sb.append(getCall())
        return sb.toString()
    }
}