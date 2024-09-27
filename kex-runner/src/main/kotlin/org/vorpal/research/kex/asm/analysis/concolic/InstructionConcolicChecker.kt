package org.vorpal.research.kex.asm.analysis.concolic


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.bfs.BfsPathSelectorManager
import org.vorpal.research.kex.asm.analysis.concolic.cgs.ContextGuidedSelectorManager
import org.vorpal.research.kex.asm.analysis.concolic.coverage.CoverageGuidedSelectorManager
import org.vorpal.research.kex.asm.analysis.concolic.weighted.WeightedPathSelectorManager
import org.vorpal.research.kex.asm.analysis.util.analyzeOrTimeout
import org.vorpal.research.kex.asm.analysis.util.checkAsync
import org.vorpal.research.kex.compile.CompilationException
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.mocking.filterMocks
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.asDescriptors
import org.vorpal.research.kex.parameters.extractFinalParameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.trace.runner.SymbolicExternalTracingRunner
import org.vorpal.research.kex.trace.runner.generateDefaultParameters
import org.vorpal.research.kex.trace.runner.generateParameters
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kex.util.testcaseDirectory
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.deleteIfExists
import kotlin.io.path.relativeTo
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

interface TestNameGenerator {
    fun generateName(method: Method, parameters: Parameters<Descriptor>): String
}

class TestNameGeneratorImpl : TestNameGenerator {
    private var testIndex = AtomicInteger(0)

    override fun generateName(method: Method, parameters: Parameters<Descriptor>): String =
        method.klassName + testIndex.getAndIncrement()

}

@ExperimentalSerializationApi
@InternalSerializationApi
class InstructionConcolicChecker(
    val ctx: ExecutionContext,
    private val pathSelector: ConcolicPathSelector,
    private val testNameGenerator: TestNameGenerator,
) {
    val cm: ClassManager
        get() = ctx.cm

    private val compilerHelper = CompilerHelper(ctx)

    private val constructedArguments: MutableList<Parameters<Descriptor>> = mutableListOf()

    private fun collectArguments(params: Parameters<Descriptor>) {
        if (!kexConfig.getBooleanValue("test", "collectArgumentFromTraces", false))
            return
        constructedArguments.add(params)
    }

    companion object {

        private fun buildSelectorManager(
            ctx: ExecutionContext,
            targets: Set<Method>,
            strategyName: String,
        ): ConcolicPathSelectorManager = when (strategyName) {
            "bfs" -> BfsPathSelectorManager(ctx, targets)
            "cgs" -> ContextGuidedSelectorManager(ctx, targets)
            "coverage" -> CoverageGuidedSelectorManager(ctx, targets)
            "weighted" -> WeightedPathSelectorManager(ctx, targets)
            else -> unreachable { log.error("Unknown type of search strategy $strategyName") }
        }

        @ExperimentalTime
        @DelicateCoroutinesApi
        fun run(context: ExecutionContext, targets: Set<Method>): Map<Method, List<Parameters<Descriptor>>> { // FIXME
            val executors = kexConfig.getIntValue("concolic", "numberOfExecutors", 8)
            val timeLimit = kexConfig.getIntValue("concolic", "timeLimit", 100)
            val searchStrategy = kexConfig.getStringValue("concolic", "searchStrategy", "bfs")

            val actualNumberOfExecutors = maxOf(1, minOf(executors, targets.size))
            val coroutineContext = newFixedThreadPoolContextWithMDC(actualNumberOfExecutors, "concolic-dispatcher")

            val selectorManager = buildSelectorManager(context, targets, searchStrategy)
            val testNameGenerator = TestNameGeneratorImpl()
            val collectedArguments = mutableMapOf<Method, List<Parameters<Descriptor>>>()

            runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit.seconds) {
                    targets.map {
                        async {
                            val instructionConcolicChecker = InstructionConcolicChecker(
                                context,
                                selectorManager.createPathSelectorFor(it),
                                testNameGenerator
                            )
                            instructionConcolicChecker.start(it)
                            collectedArguments[it] = instructionConcolicChecker.constructedArguments
                        }
                    }.awaitAll()
                }
            }
            return collectedArguments
        }
    }

    suspend fun start(method: Method) {
        method.analyzeOrTimeout(ctx.accessLevel) {
            processMethod(it)
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
            .also { collectArguments(parameters.asDescriptors) }

    private suspend fun collectTrace(method: Method, parameters: Parameters<Descriptor>): ExecutionResult? = tryOrNull {
        collectArguments(parameters)
        val generator = UnsafeGenerator(ctx, method, testNameGenerator.generateName(method, parameters))
        generator.generate(parameters)
        val testFile = generator.emit()

        try {
            compilerHelper.compileFile(testFile)
        } catch (e: CompilationException) {
            testFile.deleteIfExists()
            throw e
        }
        val result = collectTrace(generator.testKlassName)
        if (kexConfig.getBooleanValue("testGen", "generateAssertions", false)) {
            CoroutineScope(Dispatchers.Default).launch {
                var testWithAssertions: Path? = null
                try {
                    if (result is ExecutionCompletedResult) {
                        val testWithAssertionsGenerator = UnsafeGenerator(
                            ctx, method, testNameGenerator.generateName(method, parameters)
                        )

                        val finalInfoDescriptors = extractFinalParameters(result, method)
                            ?.filterMocks()
                        testWithAssertionsGenerator.generate(parameters, finalInfoDescriptors)
                        testWithAssertions = testWithAssertionsGenerator.emit()
                        compilerHelper.compileFile(testWithAssertions)

                        // delete the original test in the end (.java file), only if there were no errors with assertions
                        testFile.deleteIfExists()
                        // deleting .class file
                        val classFqnPath =
                            testFile.relativeTo(kexConfig.testcaseDirectory).toString().replaceAfterLast('.', "class")
                        kexConfig.compiledCodeDirectory.resolve(classFqnPath).deleteIfExists()
                    }
                } catch (e: Throwable) {
                    testWithAssertions?.deleteIfExists()
                    log.debug("Tests with assertion generation failed with exception:", e)
                }
            }
        }

        result
    }

    private suspend fun collectTrace(klassName: String): ExecutionResult {
        val runner = SymbolicExternalTracingRunner(ctx)
        return runner.run(klassName, ExecutorTestCasePrinter.SETUP_METHOD, ExecutorTestCasePrinter.TEST_METHOD)
    }

    private suspend fun check(method: Method, state: SymbolicState): ExecutionResult? = try {
        method.checkAsync(ctx, state, enableInlining = true)?.let { collectTrace(method, it) }
    } catch (e: Throwable) {
        if (e !is TimeoutCancellationException) {
            log.error("Exception during asyncCheck:", e)
        }
        null
    }

    private suspend fun initializeExecutionGraph(
        method: Method,
        pathIterator: ConcolicPathSelector
    ) {
        val initialExecution = when (val randomTrace = getRandomTrace(method)) {
            is ExecutionCompletedResult -> randomTrace
            else -> getDefaultTrace(method)
        }
        when (initialExecution) {
            is ExecutionCompletedResult -> {
                pathIterator.addExecutionTrace(method, persistentSymbolicState(), initialExecution)
            }

            else -> {
                log.warn("Failed to generate random trace for method $method: $initialExecution")
            }
        }
    }

    private suspend fun processMethod(startingMethod: Method) {
        initializeExecutionGraph(startingMethod, pathSelector)
        yield()

        while (pathSelector.hasNext()) {
            val (method, state) = pathSelector.next()
            log.debug { "Checking state: $state" }
            log.debug { "Path:\n${state.path.asState()}" }
            yield()

            val newState = check(method, state) ?: continue
            when (newState) {
                is ExecutionCompletedResult -> when {
                    newState.trace.isEmpty() -> log.warn { "Collected empty state from $state" }
                    else -> pathSelector.addExecutionTrace(method, state, newState)
                }

                else -> log.warn("Failure during execution: $newState")
            }
            yield()
        }
    }

}
