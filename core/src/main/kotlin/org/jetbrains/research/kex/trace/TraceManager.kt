package org.jetbrains.research.kex.trace

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

abstract class TraceManager<T : AbstractTrace> {
    abstract fun getTraces(method: Method): List<T>
    abstract fun addTrace(method: Method, trace: T)

    operator fun get(method: Method) = getTraces(method)
    operator fun set(method: Method, trace: T) = addTrace(method, trace)

    abstract fun isCovered(bb: BasicBlock): Boolean
    fun isBodyCovered(method: Method) = method.bodyBlocks.map { isCovered(it) }.run {
        when {
            this.isEmpty() -> false
            else -> reduce { acc, b -> acc && b }
        }
    }
    fun isFullCovered(method: Method) = method.basicBlocks.all { isCovered(it) }
}