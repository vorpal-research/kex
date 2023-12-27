package org.vorpal.research.kex.asm.analysis.concolic

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.util.SuspendableIterator
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kfg.ir.Method

interface ConcolicPathSelectorManager {
    val ctx: ExecutionContext
    val targets: Set<Method>
    fun createPathSelectorFor(target: Method): ConcolicPathSelector
}

interface ConcolicPathSelector : SuspendableIterator<Pair<Method, PersistentSymbolicState>> {
    val ctx: ExecutionContext

    suspend fun isEmpty(): Boolean
    suspend fun addExecutionTrace(method: Method, checkedState: PersistentSymbolicState, result: ExecutionCompletedResult)
    fun reverse(pathClause: PathClause): PathClause?
}
