package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Location

class CallPredicate : Predicate {
    constructor(callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
            : super(type, location, listOf(callTerm)) {
        hasLhv = false
    }

    constructor(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
            : super(type, location, listOf(lhv, callTerm)) {
        hasLhv = true
    }

    val hasLhv: Boolean
    val lhv get() = if (hasLhv) operands[0] else unreachable { log.error("Trying to get lhv of void call") }
    val call get() = if (hasLhv) operands[1] else operands[0]

    fun getLhvUnsafe() = if (hasLhv) operands[0] else null

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tlhv = if (hasLhv) t.transform(lhv) else null
        val tcall = t.transform(call)
        return when {
            hasLhv -> when {
                tlhv == lhv && tcall == call -> this
                else -> t.pf.getCall(tlhv!!, tcall, type)
            }
            else -> when {
                tcall == call -> this
                else -> t.pf.getCall(tcall, type)
            }
        }
    }

    override fun print(): String {
        val sb = StringBuilder()
        if (hasLhv) sb.append("$lhv = ")
        sb.append(call)
        return sb.toString()
    }
}