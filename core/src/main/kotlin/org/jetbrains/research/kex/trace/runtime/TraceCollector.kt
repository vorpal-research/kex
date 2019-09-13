package org.jetbrains.research.kex.trace.runtime

import org.jetbrains.research.kex.collections.stackOf
import org.jetbrains.research.kex.trace.file.UnknownNameException
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.type.parseDesc

class TraceCollector(val cm: ClassManager) {
    val trace = arrayListOf<RuntimeAction>()
    private val stack = stackOf<Method>()

    private fun String.toType() = parseDesc(cm.type, this)

    private fun parseMethod(className: String, methodName: String, args: Array<String>, retType: String): Method {
        val klass = cm.getByName(className)
        return klass.getMethod(methodName, MethodDesc(args.map { it.toType() }.toTypedArray(), retType.toType()))
    }

    private fun parseBlock(blockName: String): BasicBlock {
        val st = stack.peek().slottracker
        return st.getBlock(blockName) ?: throw UnknownNameException(blockName)
    }

    fun addAction(action: RuntimeAction) = trace.add(action)

    fun methodEnter(className: String, methodName: String, argTypes: Array<String>, retType: String,
                    instance: Any?, args: Array<Any?>) {
        val method = parseMethod(className, methodName, argTypes, retType)
        addAction(MethodEntry(method, instance, args))
        stack.push(method)
    }

    fun methodReturn() {
        val method = stack.pop()
        addAction(MethodReturn(method, null))
    }

    fun methodReturn(value: Any) {
        val method = stack.pop()
        addAction(MethodReturn(method, value))
    }


    fun methodThrow(throwable: Throwable) {
        val method = stack.pop()
        addAction(MethodThrow(method, throwable))
    }

    fun blockEnter(blockName: String) {
        val block = parseBlock(blockName)
        addAction(BlockEntry(block))
    }

    fun blockJump(blockName: String) {
        val block = parseBlock(blockName)
        addAction(BlockJump(block))
    }

    fun blockBranch(blockName: String, condition: Any?, expected: Any?) {
        val block = parseBlock(blockName)
        addAction(BlockBranch(block, condition, expected))
    }

    fun blockSwitch(blockName: String, key: Any?) {
        val block = parseBlock(blockName)
        addAction(BlockSwitch(block, key))
    }
}


object TraceCollectorProxy {
    lateinit var collector: TraceCollector
        private set

    @JvmStatic
    fun initCollector(cm: ClassManager) {
        collector = TraceCollector(cm)
    }

    @JvmStatic
    fun currentCollector() = collector
}