package org.jetbrains.research.kex.asm.analysis.concolic

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.collections.stackOf
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.predicate.predicate
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.ConcolicFilter
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.*
import org.jetbrains.research.kex.trace.runner.ObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.RandomObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.util.*

private fun Predicate.inverse(): Predicate = when (this) {
    is EqualityPredicate -> when (rhv) {
        term { const(true) } -> predicate(type, location) { lhv equality false }
        term { const(false) } -> predicate(type, location) { lhv equality true }
        else -> this
    }
    else -> this
}

private val timeLimit by lazy { kexConfig.getLongValue("concolic", "timeLimit", 10000L) }

class ConcolicChecker(override val cm: ClassManager,
                      val loader: ClassLoader,
                      val manager: TraceManager<Trace>) : MethodVisitor {
    val random: Randomizer = defaultRandomizer
    val paths = mutableSetOf<PredicateState>()

    override fun cleanup() {
        paths.clear()
    }

    override fun visit(method: Method) {
        if (method.isStaticInitializer || method.isAbstract) return
        if (method.isConstructor) return

        try {
            runBlocking {
                withTimeout(timeLimit) {
                    process(method)
                }
            }
        } catch (e: TimeoutCancellationException) {
            return
        } catch (e: TimeoutException) {
            return
        }
    }

    private fun buildState(method: Method, trace: Trace): PredicateState {
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

    private fun mutate(ps: PredicateState): PredicateState {
        var found = false
        lateinit var last: Predicate
        val filtered = ps.dropLastWhile {
            when {
                found -> true
                it.type == PredicateType.Path() -> {
                    found = true
                    last = it
                    true
                }
                else -> false
            }
        }
        return when {
            found -> filtered + last.inverse()
            else -> filtered
        }
    }

    private fun generateByModel(method: Method, ps: PredicateState, model: SMTModel): Pair<Any?, Array<Any?>> {
        val reanimated = executeModel(ps, cm.type, method, model, loader, random)
        log.debug("Reanimated: ${tryOrNull { model.toString() }}")

        val instance = reanimated.instance ?: when {
            method.isStatic -> null
            else -> tryOrNull {
                val klass = loader.loadClass(types.getRefType(method.`class`))
                random.next(klass)
            }
        }

        if (instance == null && !method.isStatic) {
            throw GenerationException("Unable to create or generate instance of class ${method.`class`}")
        }
        return instance to reanimated.arguments.toTypedArray()
    }

    private fun collectTrace(method: Method, instance: Any?, args: Array<Any?>): Trace {
        val runner = ObjectTracingRunner(method, loader)
        return runner.collectTrace(instance, args)
    }

    private fun getRandomTrace(method: Method) = RandomObjectTracingRunner(method, loader).run()

    private suspend fun process(method: Method) {
        val traces = ArrayDeque<Trace>()
        while (!manager.isBodyCovered(method)) {
            if (traces.isEmpty()) {
                val randomTrace = getRandomTrace(method) ?: return
                manager[method] = randomTrace
                traces.add(randomTrace)
                yield()
            }
            val candidate = traces.poll()
            val result = run(method, candidate)
            if (result != null) {
                manager[method] = result
                traces.add(result)
            }
            yield()
        }
    }

    private suspend fun run(method: Method, trace: Trace): Trace? {
        val state = buildState(method, trace)
        val mutated = mutate(state)
        val path = mutated.filterByType(PredicateType.Path())
        if (path in paths) return null
        paths += path

        val checker = Checker(method, loader, PredicateStateAnalysis(cm))
        val result = checker.check(mutated)
        if (result is Result.SatResult) {
            val (instance, args) = try {
                generateByModel(method, checker.state, result.model)
            } catch (e: GenerationException) {
                log.warn(e.message)
                return null
            }
            yield()

            return try {
                collectTrace(method, instance, args)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
