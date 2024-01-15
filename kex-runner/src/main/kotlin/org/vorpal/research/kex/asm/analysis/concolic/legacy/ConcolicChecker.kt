package org.vorpal.research.kex.asm.analysis.concolic.legacy

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.wrapper
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.random.Randomizer
import org.vorpal.research.kex.reanimator.ParameterGenerator
import org.vorpal.research.kex.reanimator.Reanimator
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.inverse
import org.vorpal.research.kex.trace.TraceManager
import org.vorpal.research.kex.trace.`object`.ActionTrace
import org.vorpal.research.kex.trace.`object`.BlockAction
import org.vorpal.research.kex.trace.`object`.BlockBranch
import org.vorpal.research.kex.trace.`object`.BlockJump
import org.vorpal.research.kex.trace.`object`.BlockSwitch
import org.vorpal.research.kex.trace.`object`.MethodCall
import org.vorpal.research.kex.trace.`object`.MethodEntry
import org.vorpal.research.kex.trace.`object`.MethodReturn
import org.vorpal.research.kex.trace.`object`.MethodThrow
import org.vorpal.research.kex.trace.`object`.StaticInitEntry
import org.vorpal.research.kex.trace.`object`.StaticInitExit
import org.vorpal.research.kex.trace.runner.ObjectTracingRunner
import org.vorpal.research.kex.trace.runner.RandomObjectTracingRunner
import org.vorpal.research.kex.util.TimeoutException
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.nameMapper
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.collection.firstOrElse
import org.vorpal.research.kthelper.collection.stackOf
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull

private val timeLimit by lazy {
    kexConfig.getLongValue("concolic", "timeLimit", 10000L)
}
private val onlyMain by lazy {
    kexConfig.getBooleanValue("concolic", "mainOnly", false)
}

@Deprecated(
    "outdated version",
    replaceWith = ReplaceWith(
        "InstructionConcolicChecker",
        "org.vorpal.research.kex.asm.analysis.concolic.InstructionConcolicChecker"
    )
)
class ConcolicChecker(
    val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis,
    private val manager: TraceManager<ActionTrace>
) : MethodVisitor {
    override val cm: ClassManager get() = ctx.cm
    val loader: ClassLoader get() = ctx.loader
    val random: Randomizer get() = ctx.random
    private val nameContext = NameMapperContext()
    private val paths = mutableSetOf<PredicateState>()
    private var counter = 0
    lateinit var generator: ParameterGenerator
        private set

    override fun cleanup() {
        paths.clear()
        nameContext.clear()
    }

    private fun initializeGenerator(method: Method) {
        generator = Reanimator(ctx, psa, method)
    }

    private fun analyze(method: Method) {
        log.debug(method.print())
        initializeGenerator(method)
        try {
            runBlocking {
                withTimeout(timeLimit) {
                    try {
                        process(method)
                    } catch (e: TimeoutException) {
                        log.debug("Timeout on running {}", method)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            log.debug("Processing of method {} is stopped due timeout", method)
        }
        generator.emit()
    }

    override fun visit(method: Method) {
        if (method.isStaticInitializer || method.isAbstract) return

        if ((onlyMain && method.name == "main") || !onlyMain) {
            analyze(method)
        }
    }

    private fun buildState(method: Method, trace: ActionTrace): PredicateState {
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
        }.dropWhile { !(it is MethodEntry && it.method == method.wrapper) }

        val builder = ConcolicStateBuilder(cm, psa)
        for ((index, action) in filteredTrace.withIndex()) {
            when (action) {
                is MethodEntry -> {
                    methodStack.push(action.method.unwrap(cm))
                    prevBlockStack.push(BlockWrapper(null))
                    builder.enterMethod(action.method.unwrap(cm))
                }

                is MethodReturn -> {
                    val exitMethod = methodStack.pop()
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block.unwrap(exitMethod)
                    val next = (filteredTrace.getOrNull(index + 1) as? BlockAction)?.block?.unwrap(methodStack.peek())

                    builder.build(current, prevBlock.block, next)
                    builder.exitMethod(action.method.unwrap(cm))
                }

                is MethodThrow -> {
                    val exitMethod = methodStack.pop()
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block.unwrap(exitMethod)
                    val next = (filteredTrace.getOrNull(index + 1) as? BlockAction)?.block?.unwrap(methodStack.peek())

                    builder.build(current, prevBlock.block, next)
                    builder.exitMethod(action.method.unwrap(cm))
                }

                is MethodCall -> {
                    val currentMethod = methodStack.peek()
                    val nm = currentMethod.nameMapper
                    val mappings = mutableMapOf<Value, Value>()
                    action.instance?.run { mappings[values.getThis(currentMethod.klass)] = this.unwrap(nm) }
                    action.args.withIndex().forEach { (index, arg) ->
                        mappings[values.getArgument(index, currentMethod, currentMethod.argTypes[index])] =
                            arg.unwrap(nm)
                    }
                    builder.callMethod(
                        action.method.unwrap(cm),
                        ConcolicStateBuilder.CallParameters(action.returnValue?.unwrap(nm), mappings)
                    )
                }

                is BlockJump -> {
                    val currentMethod = methodStack.peek()
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block.unwrap(currentMethod)
                    val next = (filteredTrace.getOrNull(index + 1) as? BlockAction)?.block?.unwrap(currentMethod)

                    builder.build(current, prevBlock.block, next)
                    prevBlockStack.push(current.wrap())
                }

                is BlockBranch -> {
                    val currentMethod = methodStack.peek()
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block.unwrap(currentMethod)
                    val next = (filteredTrace.getOrNull(index + 1) as? BlockAction)?.block?.unwrap(currentMethod)

                    builder.build(current, prevBlock.block, next)
                    prevBlockStack.push(current.wrap())
                }

                is BlockSwitch -> {
                    val currentMethod = methodStack.peek()
                    val prevBlock = prevBlockStack.pop()
                    val current = action.block.unwrap(currentMethod)
                    val next = (filteredTrace.getOrNull(index + 1) as? BlockAction)?.block?.unwrap(currentMethod)

                    builder.build(current, prevBlock.block, next)
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
                val current = it.inverse(ctx.random)
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

    private fun collectTrace(method: Method, instance: Any?, args: List<Any?>): ActionTrace? {
        val params = Parameters(instance, args, setOf())
        val runner = ObjectTracingRunner(nameContext, method, loader, params)
        return runner.run()
    }

    private fun getRandomTrace(method: Method) =
        tryOrNull { RandomObjectTracingRunner(nameContext, method, loader, ctx.random).run() }

    private suspend fun process(method: Method) {
        val traces = ArrayDeque<ActionTrace>()
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

    private suspend fun run(method: Method, trace: ActionTrace): ActionTrace? {
        val state = buildState(method, trace)
        val mutated = mutate(state)
        val path = mutated.path
        if (path in paths) {
            log.debug("Could not generate new trace")
            return null
        }
        log.debug("Collected trace: {}", state)
        log.debug("Mutated trace: {}", mutated)

        val checker = Checker(method, ctx, psa)
        val result = checker.prepareAndCheck(mutated)
        if (result !is Result.SatResult) return null
        yield()

        val (instance, args) = tryOrNull {
            generator.generate("test${++counter}", method, checker.state, result.model)
        } ?: return null
        yield()

        val resultingTrace = tryOrNull { collectTrace(method, instance, args) } ?: return null
        if (buildState(method, resultingTrace).path.startsWith(path))
            paths += path
        return resultingTrace
    }
}
