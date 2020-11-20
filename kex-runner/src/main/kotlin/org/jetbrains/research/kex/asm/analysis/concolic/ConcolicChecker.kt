package org.jetbrains.research.kex.asm.analysis.concolic

import com.abdullin.kthelper.collection.firstOrElse
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
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.predicate.inverse
import org.jetbrains.research.kex.state.transformer.generateInputByModel
import org.jetbrains.research.kex.statistics.Statistics
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
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

private val timeLimit by lazy { kexConfig.getLongValue("concolic", "timeLimit", 10000L) }
private val onlyMain by lazy { kexConfig.getBooleanValue("concolic", "main-only", false) }

class ConcolicChecker(val ctx: ExecutionContext, val manager: TraceManager<Trace>) : MethodVisitor {
    override val cm: ClassManager get() = ctx.cm
    val loader: ClassLoader get() = ctx.loader
    val random: Randomizer get() = ctx.random
    val paths = mutableSetOf<PredicateState>()


    override fun cleanup() {
        paths.clear()
    }

    private fun analyze(method: Method) {
        log.debug(method.print())
        try {
            runBlocking {
                withTimeout(timeLimit) {
                    try {
                        val statistics = Statistics("CGS", method.toString(), 0, Duration.ZERO, 0)
                        processTree(method, statistics)
                        statistics.stopTimeMeasurement()
                        log.info(statistics.print())
                    } catch (e: TimeoutException) {
                        log.debug("Timeout on running $method")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            return
        }
    }

    override fun visit(method: Method) {
        if (method.isStaticInitializer || method.isAbstract) return

        if ((onlyMain && method.name == "main") || !onlyMain) {
            analyze(method)
            print("")
        }
    }

    private fun buildState(method: Method, trace: Trace): PredicateState {
        data class BlockWrapper(val block: BasicBlock?)
        data class CallParams(val method: Method, val receiver: Value?, val instance: Value?, val args: Array<Value>)

        fun BasicBlock.wrap() = BlockWrapper(this)

        val methodStack = stackOf<Method>()
        val prevBlockStack = stackOf<BlockWrapper>()
        val filteredTrace = trace.actions.dropWhile { !(it is MethodEntry && it.method == method) }.run {
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

    private fun getRandomTrace(method: Method) = RandomObjectTracingRunner(method, loader, ctx.random).run()

    private suspend fun process(method: Method, statistics: Statistics) {
        val traces = ArrayDeque<Trace>()
        while (!manager.isBodyCovered(method)) {
            val candidate = traces.firstOrElse {
                statistics.iterations += 1
                getRandomTrace(method)?.also { manager[method] = it }
            } ?: return
            yield()

            statistics.iterations += 1
            run(method, candidate)?.also {
                manager[method] = it
                traces.add(it)
                statistics.satNum += 1
            }
            yield()
        }
    }

    private suspend fun processTree(method: Method, statistics: Statistics) {
        var contextLevel = 1
        val contextCache = mutableSetOf<TraceGraph.Context>()
        val visitedBranches = mutableSetOf<TraceGraph.Branch>()

        val startTrace: Trace = getRandomTrace(method)?.also { manager[method] = it } ?: return
        if (startTrace.actions.isEmpty()) return
        val traces = DominatorTraceGraph(startTrace)

        while (!manager.isBodyCovered(method)) {
            statistics.iterations += 1
            var tb = traces.getTracesAndBranches().filter { it.second !in visitedBranches }
            while (tb.isEmpty()) {
                statistics.iterations += 1
                val randomTrace = getRandomTrace(method)?.also { manager[method] = it; } ?: return
                val randomBranch = TraceGraph.Branch(randomTrace.actions)
                tb = listOfNotNull(randomTrace to randomBranch).filter { it.second !in visitedBranches }
                yield()
            }

            tb.asSequence()
                    .filter { (_, branch) -> branch.context(contextLevel) !in contextCache }
                    .forEach { (candidate, branch) ->
                        statistics.iterations += 1
                        run(method, candidate)?.also {
                            manager[method] = it
                            traces.addTrace(it)
                            statistics.satNum += 1
                        }
                        manager[method] = candidate
                        contextCache.add(branch.context(contextLevel))
                        visitedBranches.add(traces.toBranch(candidate))
                        yield()
                    }
            contextLevel += 1
            yield()
        }
    }

    suspend fun getRandomTraceUntilSuccess(method: Method): Trace {
        var trace: Trace? = null
        while (trace == null || trace.actions.isEmpty()) {
            trace = getRandomTrace(method)?.also { manager[method] = it }
            yield()
        }
        return trace
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

        val psa = PredicateStateAnalysis(cm)
        val checker = Checker(method, loader, psa)
        val result = checker.prepareAndCheck(mutated)
        if (result !is Result.SatResult) return null
        yield()

        val (instance, args) = tryOrNull {
            generateInputByModel(ctx, method, checker.state, result.model)
        } ?: return null
        yield()

        val resultingTrace = tryOrNull { collectTrace(method, instance, args) } ?: return null
        if (buildState(method, resultingTrace).path.startsWith(path))
            paths += path
        return resultingTrace
    }
}