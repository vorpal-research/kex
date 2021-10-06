package org.jetbrains.research.kex.trace.symbolic

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.descriptor.Descriptor

@Serializable
sealed class ExecutionResult {
    abstract val trace: SymbolicState
}

@Serializable
data class ExceptionResult(
    val cause: @Contextual Descriptor,
    override val trace: @Contextual SymbolicState
) : ExecutionResult()

@Serializable
data class SuccessResult(
    override val trace: @Contextual SymbolicState
) : ExecutionResult()