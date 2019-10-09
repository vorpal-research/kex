package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.collections.stackOf
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.ConcolicFilter
import org.jetbrains.research.kex.trace.`object`.*
import org.jetbrains.research.kex.trace.runner.RandomObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.visitor.MethodVisitor

class ConcolicChecker(override val cm: ClassManager, val loader: ClassLoader) : MethodVisitor {
    override fun cleanup() {}

    override fun visit(method: Method) {
        if (method.isStaticInitializer || method.isAbstract) return
        val random = RandomObjectTracingRunner(method, loader)

        try {
            log.debug(method.print())
            val firstTrace = random.run() ?: return
            val state = buildState(method, firstTrace)
            log.debug(state)

            val checker = Checker(method, loader, PredicateStateAnalysis(cm))
            val result = checker.check(state)
            log.debug(result)
        } catch (e: TimeoutException) {
            return
        }
    }

    fun buildState(method: Method, trace: Trace): PredicateState {
        data class BlockWrapper(val block: BasicBlock?)
        data class CallParams(val method: Method, val receiver: Value?, val instance: Value?, val args: Array<Value>)

        fun BasicBlock.wrap() = BlockWrapper(this)

        val methodStack = stackOf<Method>()
        val prevBlockStack = stackOf<BlockWrapper>()
        val filteredTrace = trace.actions.dropWhile { !(it is MethodEntry && it.method == method) }

        val builder = ConcolicStateBuilder(cm)
        var methodParams: CallParams? = null
        for ((index, action) in filteredTrace.withIndex()) {
            when (action) {
                is MethodEntry -> {
                    methodStack.push(action.method)
                    prevBlockStack.push(BlockWrapper(null))
                    if (methodParams != null && methodParams.method == action.method) {
                        val mappings = mutableMapOf<Value, Value>()
                        methodParams.instance?.run { mappings[values.getThis(action.method.`class`)] = this }
                        methodParams.args.withIndex().forEach { (index, arg) ->
                            mappings[values.getArgument(index, action.method, action.method.argTypes[index])] = arg
                        }
                        builder.enterMethod(action.method, ConcolicStateBuilder.CallParameters(methodParams.receiver, mappings))
                    } else {
                        builder.enterMethod(action.method)
                    }
                    methodParams = null
                }
                is MethodReturn -> {
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block
                    val next = filteredTrace.getOrNull(index + 1) as? BlockAction
                    builder.build(current, prevBlock.block, next?.block)
                    methodStack.pop()
                    builder.exitMethod(action.method)
                }
                is MethodThrow -> {
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block
                    val next = filteredTrace.getOrNull(index + 1) as? BlockAction
                    builder.build(current, prevBlock.block, next?.block)
                    methodStack.pop()
                    builder.exitMethod(action.method)
                }
                is MethodCall -> {
                    methodParams = CallParams(action.method, action.returnValue, action.instance, action.args)
                }

                is BlockJump -> {
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block
                    val next = filteredTrace.getOrNull(index + 1) as? BlockAction
                    builder.build(current, prevBlock.block, next?.block)
                    prevBlockStack.push(current.wrap())
                }
                is BlockBranch -> {
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block
                    val next = filteredTrace.getOrNull(index + 1) as? BlockAction
                    builder.build(current, prevBlock.block, next?.block)
                    prevBlockStack.push(current.wrap())
                }
                is BlockSwitch -> {
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block
                    val next = filteredTrace.getOrNull(index + 1) as? BlockAction
                    builder.build(current, prevBlock.block, next?.block)
                    prevBlockStack.push(current.wrap())
                }
            }
        }
        return ConcolicFilter().apply(builder.apply())
    }

}
