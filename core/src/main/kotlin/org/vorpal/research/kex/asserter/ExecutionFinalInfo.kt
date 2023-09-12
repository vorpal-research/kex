package org.vorpal.research.kex.asserter

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.trace.symbolic.protocol.*

sealed class ExecutionFinalInfo(val instance: Descriptor?, val args: List<Descriptor>, val retValue: Descriptor?)

class ExecutionSuccessFinalInfo(instance: Descriptor?, args: List<Descriptor>, retValue: Descriptor?) :
        ExecutionFinalInfo(instance, args, retValue) {

    override fun equals(other: Any?): Boolean {
        if (other !is ExecutionSuccessFinalInfo) return false
        return instance == other.instance && args.containsAll(other.args) && other.args.containsAll(args) && retValue == other.retValue
    }

    override fun hashCode(): Int = (instance?.hashCode() ?: 0) * 239 + args.hashCode() * 101 + (retValue?.hashCode() ?: 0)

}

class ExecutionExceptionFinalInfo(instance: Descriptor?, args: List<Descriptor>, retValue: Descriptor?,
        val exception: Descriptor): ExecutionFinalInfo(instance, args, retValue) {

}

fun ExecutionResult.extractFinalInfo(): ExecutionFinalInfo? = when (this) {
    is ExecutionCompletedResult -> {
        val instance = trace.concreteValueMap.filterKeys { it.name == "this" }.values.firstOrNull()?.deepCopy()
        val args = trace.concreteValueMap.filterKeys { it is ArgumentTerm }.values.map { it.deepCopy() }
        val retValue = null
        when(this) {
            is ExceptionResult -> ExecutionExceptionFinalInfo(instance, args, retValue, this.cause)
            is SuccessResult -> ExecutionSuccessFinalInfo(instance, args, retValue)
        }
    }
    else -> null
}

fun Parameters<Descriptor>.extractFinalInfo(): ExecutionFinalInfo = ExecutionSuccessFinalInfo(instance, arguments, null)
