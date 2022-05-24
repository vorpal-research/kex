package org.vorpal.research.kex.asm.analysis.concolic

import org.vorpal.research.kex.trace.symbolic.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory

interface SuspendableIterator<T>  {
    suspend fun hasNext(): Boolean
    suspend fun next(): T
}

interface PathSelector : SuspendableIterator<SymbolicState> {
    val tf: TypeFactory

    suspend fun addExecutionTrace(method: Method, result: ExecutionCompletedResult)
}
