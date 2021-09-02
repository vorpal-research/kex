package org.jetbrains.research.kex.asm.analysis.concolic

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.manager.isImpactable
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.compile.JavaCompilerDriver
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.parameters.asDescriptors
import org.jetbrains.research.kex.parameters.concreteParameters
import org.jetbrains.research.kex.reanimator.ExecutionGenerator
import org.jetbrains.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.runner.SymbolicExternalTracingRunner
import org.jetbrains.research.kex.trace.runner.generateParameters
import org.jetbrains.research.kex.trace.symbolic.*
import org.jetbrains.research.kex.util.getJunit
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.BranchInst
import org.jetbrains.research.kfg.ir.value.instruction.SwitchInst
import org.jetbrains.research.kfg.ir.value.instruction.TableSwitchInst
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.logging.warn
import org.jetbrains.research.kthelper.tryOrNull
import java.nio.file.Path

private class CompilerHelper(val ctx: ExecutionContext) {
    private val junitJar = getJunit()!!
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
    val compileDir = outputDir.resolve(
        kexConfig.getPathValue("compile", "compileDir", "compiled/")
    ).also {
        it.toFile().mkdirs()
    }

    fun compileFile(file: Path) {
        val compilerDriver = JavaCompilerDriver(
            listOf(*ctx.classPath.toTypedArray(), junitJar.path), compileDir
        )
        compilerDriver.compile(listOf(file))
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
class InstructionConcolicChecker(
    val ctx: ExecutionContext,
    val traceManager: TraceManager<InstructionTrace>
) : MethodVisitor {
    override val cm: ClassManager
        get() = ctx.cm

    private val paths = mutableSetOf<PathCondition>()
    private val candidates = mutableSetOf<PathCondition>()
    private val compilerHelper = CompilerHelper(ctx)

    private val timeLimit = kexConfig.getLongValue("concolic", "timeLimit", 100000L)
    private val maxFailsInARow = kexConfig.getLongValue("concolic", "maxFailsInARow", 50)

    override fun cleanup() {
        paths.clear()
    }

    override fun visit(method: Method) {
        if (method.isStaticInitializer || !method.hasBody) return
        if (!method.isImpactable) return

        log.debug { "Processing method $method" }
        log.debug { method.print() }

        runBlocking {
            try {
                withTimeout(timeLimit) {
                    processMethod(method)
                }
                log.debug { "Method $method processing is finished normally" }
            } catch (e: TimeoutCancellationException) {
                log.debug { "Method $method processing is finished with timeout exception" }
            }
        }
    }

    private fun getRandomTrace(method: Method): ExecutionResult? = tryOrNull {
        val params = ctx.random.generateParameters(ctx.loader, method) ?: return null
        collectTraceFromAny(method, params)
    }

    private fun collectTraceFromAny(method: Method, parameters: Parameters<Any?>): ExecutionResult? =
        collectTrace(method, parameters.asDescriptors)

    private fun collectTrace(method: Method, parameters: Parameters<Descriptor>): ExecutionResult? = tryOrNull {
        val generator = ExecutionGenerator(ctx, method)
        generator.generate(parameters)
        val testFile = generator.emit()

        compilerHelper.compileFile(testFile)
        collectTrace(generator.testKlassName)
    }

    private fun collectTrace(klassName: String): ExecutionResult {
        val runner = SymbolicExternalTracingRunner(ctx)
        return runner.run(klassName, ExecutorTestCasePrinter.SETUP_METHOD, ExecutorTestCasePrinter.TEST_METHOD)
    }

    private fun Clause.reversed(): Clause? = when (instruction) {
        is BranchInst -> {
            val (cond, value) = with(predicate as EqualityPredicate) {
                lhv to (rhv as ConstBoolTerm).value
            }
            val reversed = Clause(instruction, path(instruction.location) {
                cond equality !value
            })
            if (paths.any { reversed in it }) null
            else reversed
        }
        is SwitchInst -> when (predicate) {
            is DefaultSwitchPredicate -> {
                val defaultSwitch = predicate as DefaultSwitchPredicate
                val switchInst = instruction as SwitchInst
                val cond = defaultSwitch.cond
                val candidates = switchInst.branches.keys.map { (it as IntConstant).value }.toSet()
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result
            }
            is EqualityPredicate -> {
                val equalityPredicate = predicate as EqualityPredicate
                val switchInst = instruction as SwitchInst
                val (cond, value) = equalityPredicate.lhv to (equalityPredicate.rhv as ConstIntTerm).value
                val candidates = switchInst.branches.keys.map { (it as IntConstant).value }.toSet() - value
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond `!in` switchInst.branches.keys.map { value(it) }
                    })
                    if (paths.any { mutated in it }) mutated else null
                }
            }
            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }
        is TableSwitchInst -> when (predicate) {
            is DefaultSwitchPredicate -> {
                val defaultSwitch = predicate as DefaultSwitchPredicate
                val switchInst = instruction as TableSwitchInst
                val cond = defaultSwitch.cond
                val candidates = switchInst.range.toSet()
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result
            }
            is EqualityPredicate -> {
                val equalityPredicate = predicate as EqualityPredicate
                val switchInst = instruction as TableSwitchInst
                val (cond, value) = equalityPredicate.lhv to (equalityPredicate.rhv as ConstIntTerm).value
                val candidates = switchInst.range.toSet() - value
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond `!in` switchInst.range.map { const(it) }
                    })
                    if (paths.any { mutated in it }) null else mutated
                }
            }
            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }
        else -> null
    }

    private fun mutateState(state: ExecutionResult): SymbolicState? {
        val predicateState = state.trace.state as BasicState
        val mutatedPathCondition = state.trace.path.toMutableList()
        var dropping = false
        var clause: Clause? = null
        val newState = predicateState.dropLastWhile {
            if (it.type is PredicateType.Path) {
                val instruction = state.trace[it]
                val reversed = Clause(instruction, it).reversed()
                if (reversed != null) {
                    clause = reversed
                    mutatedPathCondition.removeLast()
                    mutatedPathCondition += clause!!
                    if (PathConditionImpl(mutatedPathCondition) !in candidates)
                        dropping = true
                    else {
                        clause = null
                        mutatedPathCondition.removeLast()
                    }
                } else {
                    mutatedPathCondition.removeLast()
                }
            }
            dropping
        }
        if (clause == null) return null

        val mutatedState = newState.dropLast(1) + clause!!.predicate
        val mutatedValueMap = state.trace.concreteValueMap.toMutableMap()
        candidates += PathConditionImpl(mutatedPathCondition)
        clause!!.predicate.operands.forEach {
            mutatedValueMap.remove(it)
        }

        return SymbolicStateImpl(
            mutatedState,
            PathConditionImpl(mutatedPathCondition),
            mutatedValueMap,
            state.trace.termMap,
            state.trace.predicateMap,
            InstructionTrace()
        )
    }

    private fun prepareState(method: Method, state: PredicateState): PredicateState = transform(state) {
        +KexRtAdapter(cm)
        +StringMethodAdapter(ctx.cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +RecursiveInliner(PredicateStateAnalysis(cm)) { index, psa ->
            ConcreteImplInliner(
                cm.type,
                TypeInfoMap(),
                psa,
                inlineIndex = index
            )
        }
        +ArrayBoundsAdapter()
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +ReflectionInfoAdapter(method, ctx.loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ConstStringAdapter()
        +FieldNormalizer(method.cm)
    }

    private fun check(method: Method, state: SymbolicState): ExecutionResult? {
        val checker = Checker(method, ctx, PredicateStateAnalysis(cm))
        val preparedState = prepareState(method, state.state)
        val result = checker.check(preparedState, state.state.path)
        if (result !is Result.SatResult) return null

        return tryOrNull {
            val params = generateFinalDescriptors(method, ctx, result.model, checker.state)
                .concreteParameters(ctx.cm)
            collectTrace(method, params)
        }
    }

    private suspend fun processMethod(method: Method) {
        val stateDeque = dequeOf<ExecutionResult>()
        getRandomTrace(method)?.let {
            stateDeque += it
            paths += it.trace.path
            traceManager.addTrace(method, it.trace.trace)
        }
        yield()

        var failsInARow = 0
        while (stateDeque.isNotEmpty() && !traceManager.isFullCovered(method)) {
            ++failsInARow
            if (failsInARow > maxFailsInARow) {
                log.debug { "Reached maximum fails in a row for method $method" }
                return
            }

            val state = stateDeque.pollFirst()
            log.debug { "Processing state: $state" }

            val mutatedState = mutateState(state) ?: continue
            log.debug { "Mutated state: $mutatedState" }
            yield()

            val newState = check(method, mutatedState) ?: continue
            if (newState.trace.isEmpty()) {
                log.warn { "Collected empty state from $mutatedState" }
                continue
            }
            if (newState.trace.path !in paths) {
                log.debug { "New state: $newState" }

                stateDeque += newState
                paths += newState.trace.path
                failsInARow = 0
            }
            traceManager.addTrace(method, newState.trace.trace)
            yield()
        }
    }

}