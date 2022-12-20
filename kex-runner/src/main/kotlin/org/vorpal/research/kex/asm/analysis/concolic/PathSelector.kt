package org.vorpal.research.kex.asm.analysis.concolic

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.trace.symbolic.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ir.Method

interface SuspendableIterator<T>  {
    suspend fun hasNext(): Boolean
    suspend fun next(): T
}

interface PathSelector : SuspendableIterator<PersistentSymbolicState> {
    val ctx: ExecutionContext

    suspend fun isEmpty(): Boolean
    suspend fun addExecutionTrace(method: Method, result: ExecutionCompletedResult)
}
