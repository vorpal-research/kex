package org.vorpal.research.kex.trace.`object`

import org.vorpal.research.kex.asm.manager.BlockWrapper
import org.vorpal.research.kex.asm.manager.MethodWrapper
import org.vorpal.research.kex.asm.manager.ValueWrapper
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.collection.stackOf

interface TraceCollector {
    val trace: List<Action>

    fun methodEnter(
        className: String, methodName: String, argTypes: Array<String>, retType: String,
        instance: Any?, args: Array<Any?>
    )

    fun methodReturn(blockName: String)

    fun methodReturn(blockName: String, value: Any)

    fun methodThrow(blockName: String, throwable: Throwable)

    fun methodCall(
        className: String, methodName: String, argTypes: Array<String>, retType: String,
        returnValueName: String?, instanceName: String?, argNames: Array<String>
    )

    fun staticEntry(className: String)

    fun staticExit()

    fun blockEnter(blockName: String)

    fun blockJump(blockName: String)

    fun blockBranch(blockName: String, condition: Any?, expected: Any?)

    fun blockSwitch(blockName: String, key: Any?)
}

private class ActualTraceCollector(val cm: ClassManager, val ctx: NameMapperContext) : TraceCollector {
    override val trace = arrayListOf<Action>()
    private val stack = stackOf<MethodWrapper>()

    private fun parseMethod(className: String, methodName: String, args: Array<String>, retType: String): MethodWrapper {
        return MethodWrapper(className, methodName, args.toList(), retType)
    }

    private fun parseBlock(blockName: String): BlockWrapper {
        return BlockWrapper(blockName)
    }

    private fun parseValue(valueName: String): ValueWrapper {
        return ValueWrapper(valueName)
    }

    fun addAction(action: Action) = trace.add(action)

    override fun methodEnter(
        className: String, methodName: String, argTypes: Array<String>, retType: String,
        instance: Any?, args: Array<Any?>
    ) {
        val method = parseMethod(className, methodName, argTypes, retType)
        addAction(MethodEntry(method, instance, args))
        stack.push(method)
    }

    override fun methodReturn(blockName: String) {
        val block = parseBlock(blockName)
        val method = stack.pop()
        addAction(MethodReturn(method, block, null))
    }

    override fun methodReturn(blockName: String, value: Any) {
        val block = parseBlock(blockName)
        val method = stack.pop()
        addAction(MethodReturn(method, block, value))
    }

    override fun methodThrow(blockName: String, throwable: Throwable) {
        val block = parseBlock(blockName)
        val method = stack.pop()
        addAction(MethodThrow(method, block, throwable))
    }

    override fun methodCall(
        className: String, methodName: String, argTypes: Array<String>, retType: String,
        returnValueName: String?, instanceName: String?, argNames: Array<String>
    ) {
        val method = parseMethod(className, methodName, argTypes, retType)
        val retval = returnValueName?.run { parseValue(this) }
        val instance = instanceName?.run { parseValue(this) }
        val args = argNames.mapToArray { parseValue(it) }
        addAction(MethodCall(method, retval, instance, args))
    }

    override fun staticEntry(className: String) {
        val method = MethodWrapper(className, Method.STATIC_INIT_NAME, listOf(), "V")
        addAction(StaticInitEntry(method))
        stack.push(method)
    }

    override fun staticExit() {
        val method = stack.pop()
        addAction(StaticInitExit(method))
    }

    override fun blockEnter(blockName: String) {
        val block = parseBlock(blockName)
        addAction(BlockEntry(block))
    }

    override fun blockJump(blockName: String) {
        val block = parseBlock(blockName)
        addAction(BlockJump(block))
    }

    override fun blockBranch(blockName: String, condition: Any?, expected: Any?) {
        val block = parseBlock(blockName)
        addAction(BlockBranch(block, condition, expected))
    }

    override fun blockSwitch(blockName: String, key: Any?) {
        val block = parseBlock(blockName)
        addAction(BlockSwitch(block, key))
    }
}

private object TraceCollectorStub : TraceCollector {
    override val trace: List<Action> = emptyList()

    override fun methodEnter(
        className: String, methodName: String, argTypes: Array<String>, retType: String,
        instance: Any?, args: Array<Any?>
    ) {}

    override fun methodReturn(blockName: String) {}
    override fun methodReturn(blockName: String, value: Any) {}
    override fun methodThrow(blockName: String, throwable: Throwable) {}
    override fun methodCall(
        className: String, methodName: String, argTypes: Array<String>, retType: String,
        returnValueName: String?, instanceName: String?, argNames: Array<String>
    ) {}

    override fun staticEntry(className: String) {}
    override fun staticExit() {}

    override fun blockEnter(blockName: String) {}
    override fun blockJump(blockName: String) {}
    override fun blockBranch(blockName: String, condition: Any?, expected: Any?) {}
    override fun blockSwitch(blockName: String, key: Any?) {}
}


object TraceCollectorProxy {
    var collector: TraceCollector = TraceCollectorStub
        private set

    @JvmStatic
    fun enableCollector(cm: ClassManager, ctx: NameMapperContext): TraceCollector {
        collector = ActualTraceCollector(cm, ctx)
        return collector
    }

    @JvmStatic
    fun currentCollector() = collector

    @JvmStatic
    fun disableCollector() {
        collector = TraceCollectorStub
    }
}
