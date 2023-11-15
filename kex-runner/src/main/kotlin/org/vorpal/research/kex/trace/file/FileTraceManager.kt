package org.vorpal.research.kex.trace.file

import org.vorpal.research.kex.trace.TraceManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import java.util.*

@Suppress("unused")
class FileTraceManager : TraceManager<FileTrace>() {
    private val methods = hashMapOf<Method, MutableList<FileTrace>>()

    override fun getTraces(method: Method): List<FileTrace> = methods.getOrPut(method, ::arrayListOf)

    override fun addTrace(method: Method, trace: FileTrace) {
        val queue = ArrayDeque(listOf(trace))
        while (queue.isNotEmpty()) {
            val top = queue.pollFirst()!!
            methods.getOrPut(method, ::arrayListOf).add(top)
            queue.addAll(top.subTraces)
        }
    }

    override fun isCovered(bb: BasicBlock): Boolean {
        val traces = getTraces(bb.method)
        val blockInfos = traces.mapNotNull { it.blocks[bb] }.flatten()
        return blockInfos.count { it.hasOutput } > 0
    }
}
