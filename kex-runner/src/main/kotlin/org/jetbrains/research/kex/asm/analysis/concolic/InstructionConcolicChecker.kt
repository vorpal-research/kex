package org.jetbrains.research.kex.asm.analysis.concolic

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.analysis.concolic.bfs.BfsPathSelectorImpl
import org.jetbrains.research.kex.asm.analysis.concolic.cgs.ContextGuidedSelector
import org.jetbrains.research.kex.asm.manager.isImpactable
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.compile.JavaCompilerDriver
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.parameters.asDescriptors
import org.jetbrains.research.kex.parameters.concreteParameters
import org.jetbrains.research.kex.reanimator.UnsafeGenerator
import org.jetbrains.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.jetbrains.research.kex.reanimator.codegen.klassName
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.runner.SymbolicExternalTracingRunner
import org.jetbrains.research.kex.trace.runner.generateParameters
import org.jetbrains.research.kex.trace.symbolic.ExecutionResult
import org.jetbrains.research.kex.trace.symbolic.InstructionTrace
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kex.util.getJunit
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.unreachable
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

    private val compilerHelper = CompilerHelper(ctx)

    private val timeLimit = kexConfig.getLongValue("concolic", "timeLimit", 100000L)
    private val searchStrategy = kexConfig.getStringValue("concolic", "searchStrategy", "bfs")
    private var testIndex = 0

    override fun cleanup() {}

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
        val generator = UnsafeGenerator(ctx, method, method.klassName + testIndex++)
        generator.generate(parameters)
        val testFile = generator.emit()

        compilerHelper.compileFile(testFile)
        collectTrace(generator.testKlassName)
    }

    private fun collectTrace(klassName: String): ExecutionResult {
        val runner = SymbolicExternalTracingRunner(ctx)
        return runner.run(klassName, ExecutorTestCasePrinter.SETUP_METHOD, ExecutorTestCasePrinter.TEST_METHOD)
    }

    private fun prepareState(
        method: Method,
        state: PredicateState
    ): PredicateState = transform(state) {
        +KexRtAdapter(cm)
        +RecursiveInliner(PredicateStateAnalysis(cm)) { index, psa ->
            ConcolicInliner(
                ctx,
                psa,
                inlineIndex = index
            )
        }
        +ClassAdapter(cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +ReflectionInfoAdapter(method, ctx.loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ClassMethodAdapter(method.cm)
        +ConstStringAdapter(method.cm.type)
        +StringMethodAdapter(ctx.cm)
        +ConcolicArrayLengthAdapter()
        +FieldNormalizer(method.cm)
        +TypeNameAdapter(ctx.types)
    }

    private fun check(method: Method, state: SymbolicState): ExecutionResult? {
        val checker = Checker(method, ctx, PredicateStateAnalysis(cm))
        val query = state.path.asState()
        val preparedState = prepareState(method, state.state + query)
        val result = checker.check(preparedState)
        if (result !is Result.SatResult) return null

        return tryOrNull {
            val params = generateFinalDescriptors(method, ctx, result.model, checker.state)
                .concreteParameters(ctx.cm)
            collectTrace(method, params)
        }
    }

    private fun buildPathSelector(traceManager: TraceManager<InstructionTrace>) = when (searchStrategy) {
        "bfs" -> BfsPathSelectorImpl(ctx.types, traceManager)
        "cgs" -> ContextGuidedSelector(ctx.types, traceManager)
        else -> unreachable { log.error("Unknown type of search strategy $searchStrategy") }
    }

    private suspend fun processMethod(method: Method) {
        testIndex = 0
        val pathIterator = buildPathSelector(traceManager)
        getRandomTrace(method)?.let {
            pathIterator.addExecutionTrace(method, it)
        }
        yield()

        while (pathIterator.hasNext()) {
            val state = pathIterator.next()
            log.debug { "Checking state: $state" }
            yield()

            val newState = check(method, state) ?: continue
            if (newState.trace.isEmpty()) {
                log.warn { "Collected empty state from $state" }
                continue
            }
            pathIterator.addExecutionTrace(method, newState)
            yield()
        }
    }

}