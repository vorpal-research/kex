package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.symbolic.ExecutionResult
import org.jetbrains.research.kex.trace.symbolic.InstructionTrace
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kfg.ir.Method

interface PathSelector : Iterator<SymbolicState> {
    val traceManager: TraceManager<InstructionTrace>

    fun addExecutionTrace(method: Method, result: ExecutionResult)
}
