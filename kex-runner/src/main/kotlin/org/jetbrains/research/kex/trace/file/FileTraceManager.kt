package org.jetbrains.research.kex.trace.file

import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import java.util.*

class FileTraceManager : TraceManager<Trace> {
    private val methods = hashMapOf<Method, MutableList<Trace>>()

    override fun getTraces(method: Method): List<Trace> = methods.getOrPut(method, ::arrayListOf)

    override fun addTrace(method: Method, trace: Trace) {
        val queue = ArrayDeque(listOf(trace))
        while (queue.isNotEmpty()) {
            val top = queue.pollFirst()!!
            methods.getOrPut(method, ::arrayListOf).add(top)
            queue.addAll(top.subtraces)
        }
    }

    override fun isCovered(method: Method, bb: BasicBlock): Boolean {
        val traces = getTraces(method)
        val blockInfos = traces.mapNotNull { it.blocks[bb] }.flatten()
        return blockInfos.count { it.hasOutput } > 0
    }
}