package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Location

class CallPredicate : Predicate {
    val hasLhv: Boolean

    constructor(callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
            : super(type, location, listOf(callTerm)) {
        hasLhv = false
    }

    constructor(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
            : super(type, location, listOf(lhv, callTerm)) {
        hasLhv = true
    }

    fun getLhvUnsafe() = if (hasLhv) operands[0] else null
    fun getLhv() = if (hasLhv) operands[0] else unreachable { log.error("Trying to get lhv of void call") }
    fun getCall() = if (hasLhv) operands[1] else operands[0]

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val lhv = if (hasLhv) t.transform(getLhv()) else null
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