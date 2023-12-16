package org.vorpal.research.kex.asm.analysis.concolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.bfs.BfsPathSelectorImpl
import org.vorpal.research.kex.asm.analysis.concolic.cgs.ContextGuidedSelector
import org.vorpal.research.kex.asm.analysis.concolic.gui.GUIProxySelector
import org.vorpal.research.kex.asm.analysis.util.analyzeOrTimeout
import org.vorpal.research.kex.asm.analysis.util.checkAsync
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.asDescriptors
import org.vorpal.research.kex.reanimator.ExecutionFinalInfoGenerator
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.trace.runner.SymbolicExternalTracingRunner
import org.vorpal.research.kex.trace.runner.generateDefaultParameters
import org.vorpal.research.kex.trace.runner.generateParameters
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.SuccessResult
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.tryOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.deleteIfExists
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@ExperimentalSerializationApi
@InternalSerializationApi
class InstructionConcolicChecker(
    val ctx: ExecutionContext,
) {
    val cm: ClassManager
        get() = ctx.cm

    private val compilerHelper = CompilerHelper(ctx)

    private val searchStrategy = kexConfig.getStringValue("concolic", "searchStrategy", "bfs")
    private val guiEnabled = kexConfig.getBooleanValue("gui", "enabled", false)

    private var testIndex = AtomicInteger(0)

    companion object {
        @ExperimentalTime
        @DelicateCoroutinesApi
        fun run(context: ExecutionContext, targets: Set<Method>) {
            val executors = kexConfig.getIntValue("concolic", "numberOfExecutors", 8)
            val timeLimit = kexConfig.getIntValue("concolic", "timeLimit", 100)

            val actualNumberOfExecutors = maxOf(1, minOf(executors, targets.size))
            val coroutineContext = newFixedThreadPoolContextWithMDC(actualNumberOfExecutors, "concolic-dispatcher")
            runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit.seconds) {
                    targets.map {
                        async { InstructionConcolicChecker(context).visit(it) }
                    }.awaitAll()
                }
            }
        }
    }

    suspend fun visit(method: Method) {
        method.analyzeOrTimeout(ctx.accessLevel) {
            processMethod(it)
        }
    }

    private suspend fun getDefaultTrace(method: Method): ExecutionResult? = try {
        // mark_4: instead of random generation, it uses some predefined values - false for booleans, 0 for numerical
        // values, etc.
        val params = generateDefaultParameters(ctx.loader, method)
        params?.let { collectTraceFromAny(method, it) }
    } catch (e: Throwable) {
        log.warn("Error while collecting random trace:", e)
        null
    }

    private suspend fun getRandomTrace(method: Method): ExecutionResult? = try {
        val params = ctx.random.generateParameters(ctx.loader, method)
        // mark_2: generation of random parameters for method
        params?.let { collectTraceFromAny(method, it) }
    } catch (e: Throwable) {
        log.warn("Error while collecting random trace:", e)
        null
    }

    private suspend fun collectTraceFromAny(method: Method, parameters: Parameters<Any?>): ExecutionResult? =
        collectTrace(method, parameters.asDescriptors)

    private suspend fun collectTrace(method: Method, parameters: Parameters<Descriptor>): ExecutionResult? = tryOrNull {
        // mark_3: (?) as far as I understood generator transform parameters and method to code that can be
        // executed using symbolic execution and runs it, collecting trace
        val generator = UnsafeGenerator(ctx, method, method.klassName + testIndex.getAndIncrement())
        generator.generate(parameters)
        val testFile = generator.emit()

        compilerHelper.compileFile(testFile)
        val result = collectTrace(generator.testKlassName)
        log.debug(result)
        try {
            if (result is ExecutionCompletedResult) {
                val executionFinalInfoGenerator = ExecutionFinalInfoGenerator(ctx, method)
                val testWithAssertionsGenerator =
                    UnsafeGenerator(ctx, method, method.klassName + testIndex.getAndIncrement())
                val finalInfoDescriptors = executionFinalInfoGenerator.extractFinalInfo(result)
                log.debug("Comparing input parameters and final descriptors:")
                log.debug(parameters)
                log.debug(finalInfoDescriptors)
                testWithAssertionsGenerator.generate(
                    parameters,
                    executionFinalInfoGenerator.generateFinalInfoActionSequences(finalInfoDescriptors)
                )
                val testFile2 = testWithAssertionsGenerator.emit()

                compilerHelper.compileFile(testFile2)
                testFile.deleteIfExists()
            }
            result
        } catch (e: Exception) {
            log.debug(e.stackTrace)
            result
        }
    }

    private suspend fun collectTrace(klassName: String): ExecutionResult {
        // mark_9: this method is executing test, but it only sends it to someone else. Who really executes it?
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

    private fun buildPathSelector(): ConcolicPathSelector {
        val pathSelector = when (searchStrategy) {
            "bfs" -> BfsPathSelectorImpl(ctx)
            "cgs" -> ContextGuidedSelector(ctx)
            else -> unreachable { log.error("Unknown type of search strategy $searchStrategy") }
        }

        return if (guiEnabled) GUIProxySelector(pathSelector) else pathSelector
    }

    private suspend fun handleStartingTrace(
        method: Method,
        pathIterator: ConcolicPathSelector,
        executionResult: ExecutionResult?
    ) {
        executionResult?.let {
            when (it) {
                is ExecutionCompletedResult -> pathIterator.addExecutionTrace(method, it)
                else -> log.warn("Failed to generate random trace: $it")
            }
        }
    }

    // mark_1: start of the process
    private suspend fun processMethod(method: Method) {
        //testIndex = AtomicInteger(0)
        val pathIterator = buildPathSelector()

        handleStartingTrace(method, pathIterator, getRandomTrace(method))
        if (pathIterator.isEmpty()) {
            handleStartingTrace(method, pathIterator, getDefaultTrace(method))
        }
        yield()

        // mark_5: in getTrace methods addExecutionTrace was called. This method runs through a log of execution and
        // adds vertex for each state predicate and edges for each path predicate. See the next mark for more info
        while (pathIterator.hasNext()) {
            val state = pathIterator.next()
            log.debug { "Checking state: $state" }
            log.debug { "Path:\n${state.path.asState()}" }
            yield()

            val newState = check(method, state) ?: continue
            if (newState is SuccessResult) {
                log.debug(newState)
            }
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
