package org.vorpal.research.kex.trace.`object`

import org.vorpal.research.kex.asm.manager.MethodWrapper
import org.vorpal.research.kex.asm.manager.wrapper
import org.vorpal.research.kex.trace.TraceManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.stackOf
import org.vorpal.research.kthelper.logging.log

class ObjectTraceManager : TraceManager<ActionTrace>() {
    private val methodInfos = mutableMapOf<MethodWrapper, MutableSet<ActionTrace>>()
    private val fullTraces = mutableSetOf<ActionTrace>()

    override fun getTraces(method: Method): List<ActionTrace> = methodInfos.getOrDefault(method.wrapper, mutableSetOf()).toList()

    override fun addTrace(method: Method, trace: ActionTrace) {
        fullTraces += trace
        val traceStack = stackOf<MutableList<Action>>()
        val methodStack = stackOf<MethodWrapper>()
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
            bb.wrapper?.let {
                methodInfos[bb.method.wrapper]?.any { trace -> trace.isCovered(it) }
            } ?: false
}