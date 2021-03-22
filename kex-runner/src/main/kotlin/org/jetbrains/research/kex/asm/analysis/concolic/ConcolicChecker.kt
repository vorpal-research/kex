package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kthelper.collection.firstOrElse
import org.jetbrains.research.kthelper.collection.stackOf
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.reanimator.Reanimator
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.predicate.inverse
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.*
import org.jetbrains.research.kex.trace.runner.ObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.RandomObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.util.*

private val timeLimit by lazy { kexConfig.getLongValue("concolic", "timeLimit", 10000L) }
private val onlyMain by lazy { kexConfig.getBooleanValue("concolic", "main-only", false) }

class ConcolicChecker(
    val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis,
    private val manager: TraceManager<Trace>
) : MethodVisitor {
    override val cm: ClassManager get() = ctx.cm
    val loader: ClassLoader get() = ctx.loader
    val random: Randomizer get() = ctx.random
    private val paths = mutableSetOf<PredicateState>()
    private var counter = 0
    lateinit var generator: Reanimator
        protected set

    override fun cleanup() {
        paths.clear()
    }

    private fun analyze(method: Method) {
        log.debug(method.print())
        generator = Reanimator(ctx, psa, method)
        try {
            runBlocking {
                withTimeout(timeLimit) {
                    try {
                        process(method)
                    } catch (e: TimeoutException) {
                        log.debug("Timeout on running $method")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            log.debug("Processing of method $method is stopped due timeout")
        }
        generator.emit()
    }

    override fun visit(method: Method) {
        if (method.isStaticInitializer || method.isAbstract) return

        if ((onlyMain && method.name == "main") || !onlyMain) {
            analyze(method)
        }
    }

    private fun buildState(method: Method, trace: Trace): PredicateState {
        data class BlockWrapper(val block: BasicBlock?)

        fun BasicBlock.wrap() = BlockWrapper(this)

        val methodStack = stackOf<Method>()
        val prevBlockStack = stackOf<BlockWrapper>()
        val filteredTrace = trace.actions.run {
            var staticLevel = 0
            filter {
                when (it) {
                    is StaticInitEntry -> {
                        ++staticLevel
                        false
                    }
                    is StaticInitExit -> {
                        --staticLevel
                        false
                    }
                    else -> staticLevel == 0
                }
            }
        }.dropWhile { !(it is MethodEntry && it.method == method) }

        val builder = ConcolicStateBuilder(cm, psa)
        for ((index, action) in filteredTrace.withIndex()) {
            when (action) {
                is MethodEntry -> {
                    methodStack.push(action.method)
                    prevBlockStack.push(BlockWrapper(null))
                    builder.enterMethod(action.method)
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
                    val mappings = mutableMapOf<Value, Value>()
                    action.instance?.run { mappings[values.getThis(action.method.`class`)] = this }
                    action.args.withIndex().forEach { (index, arg) ->
                        mappings[values.getArgument(index, action.method, action.method.argTypes[index])] = arg
                    }
                    builder.callMethod(action.method, ConcolicStateBuilder.CallParameters(action.returnValue, mappings))
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
                else -> {
                }
            }
        }
        return builder.apply()
    }

    private fun mutate(ps: PredicateState): PredicateState {
        infix fun PredicateState.covered(set: Set<PredicateState>): Boolean {
            return set.any { it.startsWith(this) }
        }

        val currentPath = StateBuilder()
        val currentState = StateBuilder()
        ps.takeWhile {
            if (it.type != PredicateType.Path()) {
                currentState += it
                false
            } else {
                val current = it.inverse()
                when {
                    (currentPath + current).apply() covered paths -> {
                        currentPath += it
                        currentState += it
                        false
                    }
                    else -> {
                        currentState += current
                        true
                    }
                }
            }
        }
        return currentState.apply()
    }

    private fun collectTrace(method: Method, instance: Any?, args: Array<Any?>): Trace {
        val runner = ObjectTracingRunner(method, loader)
        return runner.collectTrace(instance, args)
    }

    private fun getRandomTrace(method: Method) = tryOrNull { RandomObjectTracingRunner(method, loader, ctx.random).run() }

    private suspend fun process(method: Method) {
        val traces = ArrayDeque<Trace>()
        while (!manager.isBodyCovered(method)) {
            val candidate = traces.firstOrElse { getRandomTrace(method)?.also { manager[method] = it } } ?: return
            yield()

            run(method, candidate)?.also {
                manager[method] = it
                traces.add(it)
            }
            yield()
        }
    }

    private suspend fun run(method: Method, trace: Trace): Trace? {
        val state = buildState(method, trace)
        val mutated = mutate(state)
        val path = mutated.path
        if (path in paths) {
            log.debug("Could not generate new trace")
            return null
        }
        log.debug("Collected trace: $state")
        log.debug("Mutated trace: $mutated")

        val checker = Checker(method, loader, psa)
        val result = checker.prepareAndCheck(mutated)
        if (result !is Result.SatResult) return null
        yield()

        val (instance, args) = tryOrNull {
            generator.generateAPI("test${++counter}", checker.state, result.model)
        } ?: return null
        yield()

        val resultingTrace = tryOrNull { collectTrace(method, instance, args) } ?: return null
        if (buildState(method, resultingTrace).path.startsWith(path))
            paths += path
        return resultingTrace
    }
}
