package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst


interface CallResolver {
    fun resolve(state: TraverserState, inst: CallInst): List<Method>
}

interface InvokeDynamicResolver {
    data class ResolvedDynamicCall(
        val instance: Value?,
        val arguments: List<Value>,
        val method: Method
    )

    fun resolve(state: TraverserState, inst: InvokeDynamicInst): ResolvedDynamicCall?
}

class DefaultCallResolver : CallResolver, InvokeDynamicResolver {
    override fun resolve(state: TraverserState, inst: CallInst): List<Method> = emptyList()

    override fun resolve(state: TraverserState, inst: InvokeDynamicInst): InvokeDynamicResolver.ResolvedDynamicCall? = null
}
