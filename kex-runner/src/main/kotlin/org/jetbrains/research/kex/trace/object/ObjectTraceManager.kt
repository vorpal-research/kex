package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.stackOf
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

class ObjectTraceManager : TraceManager<Trace> {
    private val methodInfos = mutableMapOf<Method, MutableSet<Trace>>()
    private val fullTraces = mutableSetOf<Trace>()

    override fun getTraces(method: Method): List<Trace> = methodInfos.getOrDefault(method, mutableSetOf()).toList()

    override fun addTrace(method: Method, trace: Trace) {
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
                    methodInfos.getOrPut(methodStack.pop(), ::mutableSetOf) += Trace(methodTrace)
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
            methodInfos.getOrPut(methodStack.pop(), ::mutableSetOf) += Trace(traceStack.pop())
        }
    }

    override fun isCovered(method: Method, bb: BasicBlock): Boolean =
            methodInfos[method]?.any { it.isCovered(bb) } ?: false
}