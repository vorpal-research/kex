package org.vorpal.research.kex.asm.analysis.concolic

import org.vorpal.research.kex.trace.TraceManager
import org.vorpal.research.kex.trace.symbolic.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.InstructionTrace
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory

interface SuspendableIterator<T>  {
    suspend fun hasNext(): Boolean
    suspend fun next(): T
}

interface PathSelector : SuspendableIterator<SymbolicState> {
    val tf: TypeFactory
    val traceManager: TraceManager<InstructionTrace>

    suspend fun addExecutionTrace(method: Method, result: ExecutionResult)
}
