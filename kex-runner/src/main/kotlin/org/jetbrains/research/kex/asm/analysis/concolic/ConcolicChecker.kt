package org.jetbrains.research.kex.asm.analysis.concolic

import com.abdullin.kthelper.collection.stackOf
import com.abdullin.kthelper.logging.log
import com.abdullin.kthelper.tryOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateInputByModel
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.*
import org.jetbrains.research.kex.trace.runner.ObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.RandomObjectTracingRunner
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.visitor.MethodVisitor

private val timeLimit by lazy { kexConfig.getLongValue("concolic", "timeLimit", 50000L) }

class ConcolicChecker(val context: ExecutionContext, val psa: PredicateStateAnalysis, val tm: TraceManager<Trace>) : MethodVisitor {
    private val paths = mutableSetOf<PredicateState>()

    override val cm: ClassManager
        get() = context.cm

    override fun cleanup() {
        paths.clear()
    }

    override fun visit(method: Method) {
        if (method.isStaticInitializer || method.isAbstract) return

        log.debug("Testing method $method")
        try {
            runBlocking {
                withTimeout(timeLimit) {
                    while (!run(method)) {
                        yield()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            return
        }
    }

    private val Method.randomTrace: Trace?
        get() {
            val runner = RandomObjectTracingRunner(this, context.loader, context.random)
            return tryOrNull { runner.run() }
        }

    private fun Method.collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val runner = ObjectTracingRunner(this, context.loader)
        return runner.collectTrace(instance, args)
    }

    private fun Trace.filterToEntry(entry: Method): Trace {
        val fromEntry = actions.dropWhile { !(it is MethodEntry && it.method == entry) }
        val withoutStaticInitializers = fromEntry.run {
            var inStatic = false
            filter {
                when (it) {
                    is StaticInitEntry -> {
                        inStatic = true
                        false
                    }
                    is StaticInitExit -> {
                        inStatic = false
                        false
                    }
                    else -> !inStatic
                }
            }
        }
        return Trace(withoutStaticInitializers)
    }

    private val Trace.asState: PredicateState
        get() {
            data class BlockWrapper(val block: BasicBlock?)
            data class CallParams(val method: Method, val receiver: Value?, val instance: Value?, val args: Array<Value>)

            fun BasicBlock.wrap() = BlockWrapper(this@wrap)

            val methodStack = stackOf<Method>()
            val prevBlockStack = stackOf<BlockWrapper>()

            val builder = ConcolicStateBuilder(cm)
            var methodParams: CallParams? = null
            for ((index, action) in actions.withIndex()) {
                val connections = {
                    val prevBlock = prevBlockStack.pop()
                    val next = actions.getOrNull(index + 1) as? BlockAction
                    prevBlock.block to next?.block
                }

                val exitFromMethod = { method: Method, current: BasicBlock ->
                    val (prev, next) = connections()
                    builder.build(current, prev, next)
                    methodStack.pop()
                    builder.exitMethod(method)
                }

                val exitFromBlock = { current: BasicBlock ->
                    val (prev, next) = connections()
                    builder.build(current, prev, next)
                    prevBlockStack.push(current.wrap())
                }

                when (action) {
                    is MethodEntry -> {
                        methodStack.push(action.method)
                        prevBlockStack.push(BlockWrapper(null))
                        if (methodParams != null && methodParams.method == action.method) {
                            val mappings = mutableMapOf<Value, Value>()
                            methodParams.instance?.run { mappings[values.getThis(action.method.`class`)] = this@run }
                            methodParams.args.withIndex().forEach { (index, arg) ->
                                mappings[values.getArgument(index, action.method, action.method.argTypes[index])] = arg
                            }
                            builder.enterMethod(action.method, ConcolicStateBuilder.CallParameters(methodParams.receiver, mappings))
                        } else {
                            builder.enterMethod(action.method)
                        }
                        methodParams = null
                    }
                    is MethodReturn -> exitFromMethod(action.method, action.block)
                    is MethodThrow -> exitFromMethod(action.method, action.block)
                    is MethodCall -> methodParams = CallParams(action.method, action.returnValue, action.instance, action.args)
                    is BlockJump -> exitFromBlock(action.block)
                    is BlockBranch -> exitFromBlock(action.block)
                    is BlockSwitch -> exitFromBlock(action.block)
                    else -> {}
                }
            }
            return builder.apply()
        }

    private fun generateNewInput(method: Method, state: PredicateState): Pair<Any?, Array<Any?>>? {
        val checker = Checker(method, context.loader, psa)
        val result = checker.prepareAndCheck(state)
        if (result !is Result.SatResult) return null
        return tryOrNull { generateInputByModel(context, method, checker.state, result.model) }
    }

    private suspend fun run(method: Method): Boolean {
        var currentTrace: Trace
        while (!tm.isBodyCovered(method)) {
            yield()

            currentTrace = method.randomTrace ?: continue
            tm.addTrace(method, currentTrace)
            yield()

            do {
                currentTrace = analyzeTrace(method, currentTrace) ?: break
                tm.addTrace(method, currentTrace)
                yield()
            } while (true)
        }
        return true
    }

    private suspend fun analyzeTrace(method: Method, trace: Trace): Trace? {
        val filteredTrace = trace.filterToEntry(method)
        val state = filteredTrace.asState
        log.debug("Collected trace: $state")
        val pathCondition = state.path
        paths += pathCondition
        yield()

        val mutatedState = DfsStrategy(method, paths).next(state) ?: return null
        yield()

        log.debug("Mutated state: $mutatedState")
        val (instance, args) = generateNewInput(method, mutatedState) ?: return null
        yield()

        return tryOrNull { method.collectTrace(instance, args) }
    }
}