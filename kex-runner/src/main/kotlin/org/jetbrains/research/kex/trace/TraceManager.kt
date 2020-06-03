package org.jetbrains.research.kex.trace

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

interface TraceManager<T> {
    fun getTraces(method: Method): List<T>
    fun addTrace(method: Method, trace: T)

    operator fun get(method: Method) = getTraces(method)
    operator fun set(method: Method, trace: T) = addTrace(method, trace)

    fun isCovered(method: Method, bb: BasicBlock): Boolean
    fun isBodyCovered(method: Method) = method.bodyBlocks.map { isCovered(method, it) }.run {
        when {
            this.isEmpty() -> false
            else -> reduce { acc, b -> acc && b }
        }
    }
}
