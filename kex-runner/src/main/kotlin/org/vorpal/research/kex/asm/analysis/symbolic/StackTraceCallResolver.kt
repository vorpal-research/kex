package org.vorpal.research.kex.asm.analysis.symbolic

import ch.scheitlin.alex.java.StackTrace
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.CallInst

class StackTraceCallResolver(
    val stackTrace: StackTrace,
    val fallback: SymbolicCallResolver
) : SymbolicCallResolver {
    override fun resolve(state: TraverserState, inst: CallInst): List<Method> {
        return fallback.resolve(state, inst)
    }
}
