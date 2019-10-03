package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kex.collections.stackOf
import org.jetbrains.research.kex.trace.file.UnknownNameException
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.type.parseDesc

abstract class TraceCollector(val cm: ClassManager) {
    val trace = arrayListOf<Action>()
    protected val stack = stackOf<Method>()

    protected fun String.toType() = parseDesc(cm.type, this)

    protected fun parseMethod(className: String, methodName: String, args: Array<String>, retType: String): Method {
        val klass = cm.getByName(className)
        return klass.getMethod(methodName, MethodDesc(args.map { it.toType() }.toTypedArray(), retType.toType()))
    }

    protected fun parseBlock(blockName: String): BasicBlock {
        val st = stack.peek().slottracker
        return st.getBlock(blockName) ?: throw UnknownNameException(blockName)
    }

    fun addAction(action: Action) = trace.add(action)

    open fun methodEnter(className: String, methodName: String, argTypes: Array<String>, retType: String,
                    instance: Any?, args: Array<Any?>) {
        val method = parseMethod(className, methodName, argTypes, retType)
        addAction(MethodEntry(method, instance, args))
        stack.push(method)
    }

    open fun methodReturn(blockName: String) {
        val block = parseBlock(blockName)
        val method = stack.pop()
        addAction(MethodReturn(method, block, null))
    }

    open fun methodReturn(blockName: String, value: Any) {
        val block = parseBlock(blockName)
        val method = stack.pop()
        addAction(MethodReturn(method, block, value))
    }

    open fun methodThrow(blockName: String, throwable: Throwable) {
        val block = parseBlock(blockName)
        val method = stack.pop()
        addAction(MethodThrow(method, block, throwable))
    }

    open fun blockEnter(blockName: String) {
        val block = parseBlock(blockName)
        addAction(BlockEntry(block))
    }

    open fun blockJump(blockName: String) {
        val block = parseBlock(blockName)
        addAction(BlockJump(block))
    }

    open fun blockBranch(blockName: String, condition: Any?, expected: Any?) {
        val block = parseBlock(blockName)
        addAction(BlockBranch(block, condition, expected))
    }

    open fun blockSwitch(blockName: String, key: Any?) {
        val block = parseBlock(blockName)
        addAction(BlockSwitch(block, key))
    }
}

private class ActualTraceCollector(cm: ClassManager) : TraceCollector(cm)

private class TraceCollectorStub(cm: ClassManager) : TraceCollector(cm) {

    override fun methodEnter(className: String, methodName: String, argTypes: Array<String>, retType: String,
                         instance: Any?, args: Array<Any?>) {}

    override fun methodReturn(blockName: String) {}
    override fun methodReturn(blockName: String, value: Any) {}
    override fun methodThrow(blockName: String, throwable: Throwable) {}
    override fun blockEnter(blockName: String) {}
    override fun blockJump(blockName: String) {}
    override fun blockBranch(blockName: String, condition: Any?, expected: Any?) {}
    override fun blockSwitch(blockName: String, key: Any?) {}
}


object TraceCollectorProxy {
    lateinit var collector: TraceCollector
        private set

    @JvmStatic
    fun enableCollector(cm: ClassManager): TraceCollector {
        collector = ActualTraceCollector(cm)
        return collector
    }

    @JvmStatic
    fun currentCollector() = collector

    @JvmStatic
    fun disableCollector() {
        collector = TraceCollectorStub(collector.cm)
    }
}