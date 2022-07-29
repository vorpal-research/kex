package org.vorpal.research.kex.asm.analysis.concolic

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.annotations.AnnotationManager
import org.vorpal.research.kex.asm.analysis.concolic.bfs.BfsPathSelectorImpl
import org.vorpal.research.kex.asm.analysis.concolic.cgs.ContextGuidedSelector
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexRtManager.isJavaRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.asDescriptors
import org.vorpal.research.kex.parameters.concreteParameters
import org.vorpal.research.kex.parameters.filterStaticFinals
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kex.trace.runner.SymbolicExternalTracingRunner
import org.vorpal.research.kex.trace.runner.generateDefaultParameters
import org.vorpal.research.kex.trace.runner.generateParameters
import org.vorpal.research.kex.trace.symbolic.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.tryOrNull

@ExperimentalSerializationApi
@InternalSerializationApi
class InstructionConcolicChecker(
    val ctx: ExecutionContext,
) {
    val cm: ClassManager
        get() = ctx.cm

    private val compilerHelper = CompilerHelper(ctx)

    private val searchStrategy = kexConfig.getStringValue("concolic", "searchStrategy", "bfs")
    private var testIndex = 0

    companion object {
        @DelicateCoroutinesApi
        fun run(context: ExecutionContext, targets: Set<Method>) {
            val executors = kexConfig.getIntValue("concolic", "numberOfExecutors", 8)
            val timeLimit = kexConfig.getLongValue("concolic", "timeLimit", 100000L)

            val coroutineContext = newFixedThreadPoolContext(executors, "concolic-dispatcher")
            runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit) {
                    targets.forEach {
                        launch { InstructionConcolicChecker(context).visit(it) }
                    }
                }
            }
        }
    }

    suspend fun visit(method: Method) {
        try {
            if (method.isStaticInitializer || !method.hasBody) return
            if (!MethodManager.canBeImpacted(method, ctx.accessLevel)) return

            log.debug { "Processing method $method" }
            log.debug { method.print() }

            processMethod(method)
            log.debug { "Method $method processing is finished normally" }
        } catch (e: CancellationException) {
            log.warn { "Method $method processing is finished with timeout" }
            throw e
        }
    }

    private suspend fun getDefaultTrace(method: Method): ExecutionResult? = try {
        val params = generateDefaultParameters(ctx.loader, method)
        params?.let { collectTraceFromAny(method, it) }
    } catch (e: Throwable) {
        log.warn("Error while collecting random trace:", e)
        null
    }

    private suspend fun getRandomTrace(method: Method): ExecutionResult? = try {
        val params = ctx.random.generateParameters(ctx.loader, method)
        params?.let { collectTraceFromAny(method, it) }
    } catch (e: Throwable) {
        log.warn("Error while collecting random trace:", e)
        null
    }

    private suspend fun collectTraceFromAny(method: Method, parameters: Parameters<Any?>): ExecutionResult? =
        collectTrace(method, parameters.asDescriptors)

    private suspend fun collectTrace(method: Method, parameters: Parameters<Descriptor>): ExecutionResult? = tryOrNull {
        val generator = UnsafeGenerator(ctx, method, method.klassName + testIndex++)
        generator.generate(parameters)
        val testFile = generator.emit()

        compilerHelper.compileFile(testFile)
        collectTrace(generator.testKlassName)
    }

    private suspend fun collectTrace(klassName: String): ExecutionResult {
        val runner = SymbolicExternalTracingRunner(ctx)
        return runner.run(klassName, ExecutorTestCasePrinter.SETUP_METHOD, ExecutorTestCasePrinter.TEST_METHOD)
    }

    private fun prepareState(
        method: Method,
        state: PredicateState,
        typeMap: TypeInfoMap = emptyMap<Term, KexType>().toTypeMap()
    ): PredicateState = transform(state) {
        +KexRtAdapter(cm)
        +RecursiveInliner(PredicateStateAnalysis(cm)) { index, psa ->
            ConcolicInliner(
                ctx,
                typeMap,
                psa,
                inlineSuffix = "inlined",
                inlineIndex = index,
                kexRtOnly = false
            )
        }
        +RecursiveInliner(PredicateStateAnalysis(cm)) { index, psa ->
            ConcolicInliner(
                ctx,
                typeMap,
                psa,
                inlineSuffix = "rt.inlined",
                inlineIndex = index,
                kexRtOnly = true
            )
        }
        +ClassAdapter(cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +ReflectionInfoAdapter(method, ctx.loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ClassMethodAdapter(method.cm)
        +ConstEnumAdapter(ctx)
        +ConstStringAdapter(method.cm.type)
        +StringMethodAdapter(ctx.cm)
        +ConcolicArrayLengthAdapter()
        +FieldNormalizer(method.cm)
        +TypeNameAdapter(ctx.types)
    }

    private suspend fun check(method: Method, state: SymbolicState): ExecutionResult? {
        val checker = Checker(method, ctx, PredicateStateAnalysis(cm))
        val query = state.path.asState()
        val concreteTypeInfo = state.concreteValueMap
            .mapValues { it.value.type }
            .filterValues { it.isJavaRt }
            .mapValues { it.value.rtMapped }
            .toTypeMap()
        val preparedState = prepareState(method, state.clauses.asState() + query, concreteTypeInfo)
        log.debug { "Prepared state: $preparedState" }
        val result = checker.check(preparedState)
        if (result !is Result.SatResult) return null

        return tryOrNull {
            val params = generateFinalDescriptors(method, ctx, result.model, checker.state)
                .filterStaticFinals(cm)
                .concreteParameters(ctx.cm, ctx.random)
            log.debug { "Generated params:\n$params" }
            collectTrace(method, params)
        }
    }

    private fun buildPathSelector() = when (searchStrategy) {
        "bfs" -> BfsPathSelectorImpl(ctx)
        "cgs" -> ContextGuidedSelector(ctx)
        else -> unreachable { log.error("Unknown type of search strategy $searchStrategy") }
    }

    private suspend fun handleStartingTrace(
        method: Method,
        pathIterator: PathSelector,
        executionResult: ExecutionResult?
    ) {
        executionResult?.let {
            when (it) {
                is ExecutionCompletedResult -> pathIterator.addExecutionTrace(method, it)
                else -> log.warn("Failed to generate random trace: $it")
            }
        }
    }

    private suspend fun processMethod(method: Method) {
        testIndex = 0
        val pathIterator = buildPathSelector()

        handleStartingTrace(method, pathIterator, getRandomTrace(method))
        if (pathIterator.isEmpty()) {
            handleStartingTrace(method, pathIterator, getDefaultTrace(method))
        }
        yield()

        while (pathIterator.hasNext()) {
            val state = pathIterator.next()
            log.debug { "Checking state: $state" }
            log.debug { "Path:\n${state.path.asState()}" }
            yield()

            val newState = check(method, state) ?: continue
            when (newState) {
                is ExecutionCompletedResult -> when {
                    newState.trace.isEmpty() -> log.warn { "Collected empty state from $state" }
                    else -> pathIterator.addExecutionTrace(method, newState)
                }
                else -> log.warn("Failure during execution: $newState")
            }
            yield()
        }
    }

}
