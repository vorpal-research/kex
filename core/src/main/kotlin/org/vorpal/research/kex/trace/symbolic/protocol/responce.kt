package org.vorpal.research.kex.trace.symbolic

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.descriptor.Descriptor

@Serializable
sealed class ExecutionResult {
    abstract val trace: SymbolicState
}

@Serializable
data class ExecutionFailedResult(
    val message: String,
    override val trace: @Contextual SymbolicState
): ExecutionResult()

@Serializable
data class SetupFailedResult(
    val message: String,
    override val trace: @Contextual SymbolicState
): ExecutionResult()

@Serializable
data class ExecutionTimedOutResult(
    val message: String,
    override val trace: @Contextual SymbolicState
): ExecutionResult()

@Serializable
data class ExceptionResult(
    val cause: @Contextual Descriptor,
    override val trace: @Contextual SymbolicState
) : ExecutionResult()

@Serializable
data class SuccessResult(
    override val trace: @Contextual SymbolicState
) : ExecutionResult()