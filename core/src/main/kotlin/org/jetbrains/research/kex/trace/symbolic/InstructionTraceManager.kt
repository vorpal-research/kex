package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

class InstructionTraceManager : TraceManager<InstructionTrace>() {
    private val methodInfos = mutableMapOf<Method, MutableList<InstructionTrace>>()
    override fun getTraces(method: Method): List<InstructionTrace> = methodInfos.getOrDefault(method, listOf())

    override fun addTrace(method: Method, trace: InstructionTrace) {
        trace.trace.groupBy { it.parent.parent }.forEach { (method, insts) ->
            methodInfos.getOrPut(method, ::mutableListOf) += InstructionTrace(insts)
        }
    }

    override fun isCovered(bb: BasicBlock): Boolean = getTraces(bb.parent).any {
        bb in it.map { inst -> inst.parent }.toSet()
    }
}