package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer

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

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val lhv = if (hasLhv) t.transform(getLhv()!!) else null
        val call = t.transform(getCall())
        return when {
            lhv == getLhv() && call == getCall() -> this
            hasLhv -> t.pf.getCall(lhv!!, call, type)
            else -> t.pf.getCall(call, type)
        }
    }

    override fun print(): String {
        val sb = StringBuilder()
        if (hasLhv) sb.append("${getLhv()} = ")
        sb.append(getCall())
        return sb.toString()
    }
}