package org.vorpal.research.kex.asm.analysis.concolic

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.bfs.BfsPathSelectorImpl
import org.vorpal.research.kex.asm.analysis.concolic.cgs.ContextGuidedSelector
import org.vorpal.research.kex.asm.analysis.util.analyzeOrTimeout
import org.vorpal.research.kex.asm.analysis.util.checkAsync
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.asDescriptors
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.trace.runner.SymbolicExternalTracingRunner
import org.vorpal.research.kex.trace.runner.generateDefaultParameters
import org.vorpal.research.kex.trace.runner.generateParameters
import org.vorpal.research.kex.trace.symbolic.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.tryOrNull
import java.util.concurrent.atomic.AtomicInteger
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
        val generator = UnsafeGenerator(ctx, method, method.klassName + testIndex.getAndIncrement())
        generator.generate(parameters)
        val testFile = generator.emit()

        compilerHelper.compileFile(testFile)
        collectTrace(generator.testKlassName)
    }

    private suspend fun collectTrace(klassName: String): ExecutionResult {
        val runner = SymbolicExternalTracingRunner(ctx)
        return runner.run(klassName, ExecutorTestCasePrinter.SETUP_METHOD, ExecutorTestCasePrinter.TEST_METHOD)
    }

    private suspend fun check(method: Method, state: SymbolicState): ExecutionResult? = tryOrNull {
        method.checkAsync(ctx, state)?.let { collectTrace(method, it) }
    }

    private fun buildPathSelector() = when (searchStrategy) {
        "bfs" -> BfsPathSelectorImpl(ctx)
        "cgs" -> ContextGuidedSelector(ctx)
        else -> unreachable { log.error("Unknown type of search strategy $searchStrategy") }
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

    private suspend fun processMethod(method: Method) {
        testIndex = AtomicInteger(0)
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
