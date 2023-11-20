package org.vorpal.research.kex.trace.symbolic.protocol

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ir.value.instruction.Instruction

@Serializable
sealed class ExecutionResult

@Serializable
data class ExecutionFailedResult(
    val message: String,
): ExecutionResult()

@Serializable
data class SetupFailedResult(
    val message: String,
): ExecutionResult()

@Serializable
data class ExecutionTimedOutResult(
    val message: String,
): ExecutionResult()


@Serializable
sealed class ExecutionCompletedResult : ExecutionResult() {
    abstract val symbolicState: SymbolicState
    abstract val trace: List<Instruction>
}
@Serializable
data class ExceptionResult(
    val cause: @Contextual Descriptor,
    override val trace: List<@Contextual Instruction>,
    override val symbolicState: @Contextual SymbolicState
) : ExecutionCompletedResult()

@Serializable
data class SuccessResult(
    override val trace: List<@Contextual Instruction>,
    override val symbolicState: @Contextual SymbolicState
) : ExecutionCompletedResult()
