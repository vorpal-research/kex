package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.symbolic.ExecutionResult
import org.jetbrains.research.kex.trace.symbolic.InstructionTrace
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kfg.ir.Method

interface SuspendableIterator<T>  {
    suspend fun hasNext(): Boolean
    suspend fun next(): T
}

interface PathSelector : SuspendableIterator<SymbolicState> {
    val traceManager: TraceManager<InstructionTrace>

    suspend fun addExecutionTrace(method: Method, result: ExecutionResult)
}