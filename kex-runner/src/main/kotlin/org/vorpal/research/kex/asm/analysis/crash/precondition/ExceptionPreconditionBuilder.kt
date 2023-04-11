package org.vorpal.research.kex.asm.analysis.crash.precondition

import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.value.instruction.Instruction

interface ExceptionPreconditionBuilder {
    val targetException: Class
    fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState>
}
