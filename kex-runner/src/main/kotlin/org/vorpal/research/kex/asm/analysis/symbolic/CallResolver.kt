package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.CallInst


interface CallResolver {
    fun resolve(state: TraverserState, inst: CallInst): List<Method>
}

class DefaultCallResolver : CallResolver {
    override fun resolve(state: TraverserState, inst: CallInst): List<Method> = emptyList()

}
