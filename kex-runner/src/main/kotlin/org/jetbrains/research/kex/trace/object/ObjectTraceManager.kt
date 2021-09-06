package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.stackOf
import org.jetbrains.research.kthelper.logging.log

class ObjectTraceManager : TraceManager<ActionTrace>() {
    private val methodInfos = mutableMapOf<Method, MutableSet<ActionTrace>>()
    private val fullTraces = mutableSetOf<ActionTrace>()

    override fun getTraces(method: Method): List<ActionTrace> = methodInfos.getOrDefault(method, mutableSetOf()).toList()

    override fun addTrace(method: Method, trace: ActionTrace) {
        fullTraces += trace
        val traceStack = stackOf<MutableList<Action>>()
        val methodStack = stackOf<Method>()
        for (action in trace) {
            when (action) {
                is MethodEntry -> {
                    traceStack.push(mutableListOf(action))
                    methodStack.push(action.method)
                }
                is StaticInitEntry -> {
                    traceStack.push(mutableListOf(action))
                    methodStack.push(action.method)
                }
                is MethodReturn, is MethodThrow, is StaticInitExit -> {
                    val methodTrace = traceStack.pop()
                    methodTrace += action
                    methodInfos.getOrPut(methodStack.pop(), ::mutableSetOf) += ActionTrace(methodTrace)
                }
                is MethodCall -> { /* do nothing */ }
                is MethodAction -> unreachable { log.error("Unknown method action: $action") }
                else -> {
                    traceStack.peek() += action
                }
            }
        }
        assert(methodStack.size == traceStack.size) {
            log.error("Unexpected trace: number of method does not correspond to number of trace actions")
        }
        while (methodStack.isNotEmpty()) {
            methodInfos.getOrPut(methodStack.pop(), ::mutableSetOf) += ActionTrace(traceStack.pop())
        }
    }

    override fun isCovered(bb: BasicBlock): Boolean =
            methodInfos[bb.parent]?.any { it.isCovered(bb) } ?: false
}