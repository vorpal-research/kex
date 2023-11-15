package org.vorpal.research.kex.asm.analysis.crash

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.crash.precondition.ConstraintExceptionPrecondition
import org.vorpal.research.kex.asm.analysis.crash.precondition.ConstraintExceptionPreconditionBuilder
import org.vorpal.research.kex.asm.analysis.crash.precondition.DescriptorExceptionPreconditionBuilder
import org.vorpal.research.kex.asm.analysis.crash.precondition.ExceptionPreconditionBuilder
import org.vorpal.research.kex.asm.analysis.crash.precondition.ExceptionPreconditionBuilderImpl
import org.vorpal.research.kex.asm.analysis.crash.precondition.ExceptionPreconditionChannel
import org.vorpal.research.kex.asm.analysis.crash.precondition.ExceptionPreconditionProvider
import org.vorpal.research.kex.asm.analysis.crash.precondition.ExceptionPreconditionReceiver
import org.vorpal.research.kex.asm.analysis.symbolic.ConditionCheckQuery
import org.vorpal.research.kex.asm.analysis.symbolic.DefaultCallResolver
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicCallResolver
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicInvokeDynamicResolver
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicPathSelector
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicTraverser
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.asm.analysis.symbolic.UpdateAndReportQuery
import org.vorpal.research.kex.asm.analysis.util.checkAsyncIncremental
import org.vorpal.research.kex.asm.analysis.util.checkAsyncIncrementalAndSlice
import org.vorpal.research.kex.compile.CompilationException
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.javagen.ReflectionUtilsPrinter
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kex.util.testcaseDirectory
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.arrayIndexOOBClass
import org.vorpal.research.kfg.classCastClass
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.OuterClass
import org.vorpal.research.kfg.ir.value.ThisRef
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.negativeArrayClass
import org.vorpal.research.kfg.nullptrClass
import org.vorpal.research.kfg.runtimeException
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Files
import kotlin.coroutines.coroutineContext
import kotlin.io.path.isDirectory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

operator fun ClassManager.get(frame: StackTraceElement): Method {
    val entryClass = this[frame.className.asmString]
    return entryClass.allMethods.filter { it.name == frame.methodName }.firstOrNull { method ->
        method.body.flatten().any { inst ->
            inst.location.file == frame.fileName && inst.location.line == frame.lineNumber
        }
    }
        ?: unreachable("Could not find an owner method of\n\"\"\"$frame\"\"\"")
}

internal val Instruction.isNullptrThrowing
    get() = when (this) {
        is ArrayLoadInst -> this.arrayRef !is ThisRef
        is ArrayStoreInst -> this.arrayRef !is ThisRef
        is FieldLoadInst -> !this.isStatic && this.owner !is ThisRef
        is FieldStoreInst -> !this.isStatic && this.owner !is ThisRef
        is CallInst -> !this.isStatic && this.callee !is ThisRef
        else -> false
    }

internal fun Instruction.dominates(other: Instruction): Boolean {
    var current = this.parent
    while (current != other.parent) {
        if (current.successors.size != 1) return false
        current = current.successors.single()
    }
    return true
}


internal fun StackTrace.targetException(context: ExecutionContext): Class =
    context.cm[firstLine.takeWhile { it != ':' }.asmString]

internal fun StackTrace.targetInstructions(context: ExecutionContext): Set<Instruction> {
    val targetException = targetException(context)
    val candidates = context.cm[stackTraceLines.first()].body.flatten()
        .filter { it.location.line == stackTraceLines.first().lineNumber }
        .filterTo(mutableSetOf()) {
            when (targetException) {
                context.cm.nullptrClass -> it.isNullptrThrowing
                context.cm.arrayIndexOOBClass -> it is ArrayStoreInst || it is ArrayLoadInst
                context.cm.negativeArrayClass -> it is NewArrayInst
                context.cm.classCastClass -> it is CastInst
                else -> when (it) {
                    is ThrowInst -> targetException.asType.isSubtypeOf(it.throwable.type)
                    is CallInst -> targetException is OuterClass
                            || targetException.isInheritorOf(context.cm.runtimeException)
                            || it.method.exceptions.any { exception -> exception.isAncestorOf(targetException) }

                    else -> false
                }
            }
        }

    val candidateNewInsts = candidates.filterIsInstance<ThrowInst>().mapNotNullTo(mutableSetOf()) {
        (it.throwable as? NewInst)?.let { throwable ->
            when {
                throwable.type == targetException.asType && throwable.dominates(it) -> throwable
                else -> null
            }
        }
    }

    return when {
        candidateNewInsts.isNotEmpty() -> candidateNewInsts
        else -> candidates
    }
}

sealed class CrashReproductionResult<T> {
    abstract val preconditions: Map<String, T>
}

data class DescriptorCrashReproductionResult(
    override val preconditions: Map<String, Parameters<Descriptor>>
) : CrashReproductionResult<Parameters<Descriptor>>()

data class ConstraintCrashReproductionResult(
    override val preconditions: Map<String, ConstraintExceptionPrecondition>
) : CrashReproductionResult<ConstraintExceptionPrecondition>()


@Suppress("MemberVisibilityCanBePrivate")
abstract class AbstractCrashReproductionChecker<T>(
    ctx: ExecutionContext,
    stackTrace: StackTrace,
    protected val targetInstructions: Set<Instruction>,
    protected val preconditionProvider: ExceptionPreconditionProvider<T>,
    protected val preconditionReceiver: ExceptionPreconditionReceiver<T>,
    protected val reproductionChecker: ExceptionReproductionChecker,
) : SymbolicTraverser(ctx, ctx.cm[stackTrace.stackTraceLines.last()]) {
    abstract val result: CrashReproductionResult<T>

    override val pathSelector: SymbolicPathSelector = RandomizedDistancePathSelector(
        ctx, rootMethod, targetInstructions, stackTrace
    )
    override val callResolver: SymbolicCallResolver = StackTraceCallResolver(
        stackTrace, DefaultCallResolver(ctx)
    )
    override val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)

    protected val resultsInner = mutableMapOf<String, T>()

    init {
        ktassert(
            targetInstructions.isNotEmpty(),
            "Could not find target instructions for stack trace\n\"\"\"${stackTrace.originalStackTrace}\"\"\""
        )
    }

    protected abstract suspend fun checkSetOfPreconditions(
        inst: Instruction,
        state: TraverserState,
        preconditions: Set<PersistentSymbolicState>
    )

    private suspend fun checkNewPreconditions() {
        if (!preconditionProvider.hasNewPreconditions) return
        val newPreconditions = preconditionProvider.getNewPreconditions()
        if (newPreconditions.isEmpty()) return

        for ((key, preconditions) in newPreconditions) {
            val (checkedInst, checkedState) = key
            checkSetOfPreconditions(checkedInst, checkedState, preconditions)
        }
    }

    override suspend fun processMethod(method: Method) {
        super.processMethod(method)
        while (coroutineContext.isActive) {
            if (preconditionReceiver.stoppedReceiving) {
                preconditionProvider.stopProviding()
                break
            }
            checkNewPreconditions()
            yield()
        }
    }

    final override suspend fun traverseInstruction(state: TraverserState, inst: Instruction): TraverserState? {
        if (preconditionReceiver.stoppedReceiving) {
            preconditionProvider.stopProviding()
            return null
        }

        checkNewPreconditions()
        if (inst in targetInstructions) {
            checkSetOfPreconditions(inst, state, preconditionProvider.getPreconditions(inst, state))
        }

        return super.traverseInstruction(state, inst)
    }

    final override suspend fun checkIncremental(
        method: Method,
        state: SymbolicState,
        queries: List<SymbolicState>
    ): List<Parameters<Descriptor>?> = checkAndBuildPrecondition(method, state, queries)

    final override fun report(inst: Instruction, parameters: Parameters<Descriptor>, testPostfix: String): Boolean =
        reportAndProducePrecondition(inst, parameters, testPostfix)

    abstract suspend fun checkAndBuildPrecondition(
        method: Method,
        state: SymbolicState,
        queries: List<SymbolicState>
    ): List<Parameters<Descriptor>?>

    abstract fun reportAndProducePrecondition(
        inst: Instruction,
        parameters: Parameters<Descriptor>,
        testPostfix: String
    ): Boolean
}

class DescriptorCrashReproductionChecker(
    ctx: ExecutionContext,
    stackTrace: StackTrace,
    targetInstructions: Set<Instruction>,
    preconditionProvider: ExceptionPreconditionProvider<Parameters<Descriptor>>,
    preconditionReceiver: ExceptionPreconditionReceiver<Parameters<Descriptor>>,
    reproductionChecker: ExceptionReproductionChecker,
) : AbstractCrashReproductionChecker<Parameters<Descriptor>>(
    ctx,
    stackTrace,
    targetInstructions,
    preconditionProvider,
    preconditionReceiver,
    reproductionChecker
) {
    override val result: CrashReproductionResult<Parameters<Descriptor>>
        get() = DescriptorCrashReproductionResult(resultsInner.toMap())

    override suspend fun checkSetOfPreconditions(
        inst: Instruction,
        state: TraverserState,
        preconditions: Set<PersistentSymbolicState>
    ) {
        checkReachabilityIncremental(
            state,
            ConditionCheckQuery(
                preconditions.map { precondition ->
                    UpdateAndReportQuery(
                        precondition,
                        { newState -> newState },
                        { newState, parameters ->
                            throwExceptionAndReport(
                                newState, parameters, inst, generate(preconditionProvider.targetException.symbolicClass)
                            )
                        }
                    )
                }
            )
        )
    }

    override suspend fun checkAndBuildPrecondition(
        method: Method,
        state: SymbolicState,
        queries: List<SymbolicState>
    ): List<Parameters<Descriptor>?> = method.checkAsyncIncremental(ctx, state, queries)

    override fun reportAndProducePrecondition(
        inst: Instruction,
        parameters: Parameters<Descriptor>,
        testPostfix: String
    ): Boolean {
        if (!preconditionProvider.ready) return false
        if (inst !in targetInstructions) return false
        val testName = rootMethod.klassName + testPostfix + testIndex.getAndIncrement()
        val generator = UnsafeGenerator(ctx, rootMethod, testName)
        generator.generate(parameters)
        val testFile = generator.emit()
        try {
            compilerHelper.compileFile(testFile)
            if (!reproductionChecker.isReproduced(generator.testKlassName)) {
                return false
            }
            resultsInner[generator.testKlassName] = parameters
            preconditionReceiver.addPrecondition(parameters)
            return true
        } catch (e: CompilationException) {
            log.error("Failed to compile test file $testFile")
            return false
        }
    }
}

class ConstraintCrashReproductionChecker(
    ctx: ExecutionContext,
    stackTrace: StackTrace,
    targetInstructions: Set<Instruction>,
    preconditionProvider: ExceptionPreconditionProvider<ConstraintExceptionPrecondition>,
    preconditionReceiver: ExceptionPreconditionReceiver<ConstraintExceptionPrecondition>,
    reproductionChecker: ExceptionReproductionChecker,
) : AbstractCrashReproductionChecker<ConstraintExceptionPrecondition>(
    ctx,
    stackTrace,
    targetInstructions,
    preconditionProvider,
    preconditionReceiver,
    reproductionChecker,
) {
    override val result: CrashReproductionResult<ConstraintExceptionPrecondition>
        get() = ConstraintCrashReproductionResult(resultsInner.toMap())

    private var lastPrecondition = mutableMapOf<Parameters<Descriptor>, ConstraintExceptionPrecondition>()

    override suspend fun checkSetOfPreconditions(
        inst: Instruction,
        state: TraverserState,
        preconditions: Set<PersistentSymbolicState>
    ) {
        for (precondition in preconditions) {
            checkReachabilityIncremental(
                state + precondition,
                ConditionCheckQuery(
                    UpdateAndReportQuery(
                        persistentSymbolicState(),
                        { newState -> newState },
                        { newState, parameters ->
                            throwExceptionAndReport(
                                newState,
                                parameters,
                                inst,
                                generate(preconditionProvider.targetException.symbolicClass)
                            )
                        }
                    )
                )
            )
        }
    }


    override suspend fun checkAndBuildPrecondition(
        method: Method,
        state: SymbolicState,
        queries: List<SymbolicState>
    ): List<Parameters<Descriptor>?> {
        val result = method.checkAsyncIncrementalAndSlice(ctx, state, queries)
        lastPrecondition.putAll(result.filterNotNull().toMap())
        return result.map { it?.first }
    }

    override fun reportAndProducePrecondition(
        inst: Instruction,
        parameters: Parameters<Descriptor>,
        testPostfix: String
    ): Boolean {
        if (inst !in targetInstructions) return false
        val testName = rootMethod.klassName + testPostfix + testIndex.getAndIncrement()
        val generator = UnsafeGenerator(ctx, rootMethod, testName)
        generator.generate(parameters)
        val testFile = generator.emit()
        try {
            compilerHelper.compileFile(testFile)
            if (!reproductionChecker.isReproduced(generator.testKlassName)) {
                return false
            }
            resultsInner[generator.testKlassName] = lastPrecondition[parameters]!!
            preconditionReceiver.addPrecondition(lastPrecondition[parameters]!!)
            return true
        } catch (e: CompilationException) {
            log.error("Failed to compile test file $testFile")
            return false
        }
    }
}

object CrashReproductionChecker {

    @ExperimentalTime
    @DelicateCoroutinesApi
    fun runWithDescriptorPreconditions(context: ExecutionContext, stackTrace: StackTrace): Set<String> =
        runIteratively(
            context,
            stackTrace,
            { DescriptorExceptionPreconditionBuilder(context, stackTrace.targetException(context), emptySet()) },
            ::DescriptorCrashReproductionChecker
        )

    @ExperimentalTime
    @DelicateCoroutinesApi
    fun runWithConstraintPreconditions(context: ExecutionContext, stackTrace: StackTrace): Set<String> =
        runIteratively(
            context,
            stackTrace,
            { ConstraintExceptionPreconditionBuilder(context, stackTrace.targetException(context), emptySet()) },
            ::ConstraintCrashReproductionChecker
        )

    @ExperimentalTime
    @DelicateCoroutinesApi
    fun <T> runIteratively(
        context: ExecutionContext,
        stackTrace: StackTrace,
        preconditionBuilder: () -> ExceptionPreconditionBuilder<T>,
        crashReproductionBuilder: (
            ExecutionContext, StackTrace, targetInstructions: Set<Instruction>, ExceptionPreconditionProvider<T>,
            ExceptionPreconditionReceiver<T>, ExceptionReproductionChecker
        ) -> AbstractCrashReproductionChecker<T>
    ): Set<String> {
        val timeLimit = kexConfig.getIntValue("crash", "timeLimit", 100).seconds
        val executors = kexConfig.getIntValue("symbolic", "numberOfExecutors", 8)

        val actualNumberOfExecutors = maxOf(1, minOf(executors, stackTrace.stackTraceLines.size))
        val coroutineContext = newFixedThreadPoolContextWithMDC(actualNumberOfExecutors, "crash-dispatcher")
        return runBlocking(coroutineContext) {
            val checkers = mutableListOf<AbstractCrashReproductionChecker<*>>()
            withTimeoutOrNull(timeLimit) {
                var index = 0
                var producerChannel = ExceptionPreconditionChannel<T>(
                    "${index++}",
                    ExceptionPreconditionBuilderImpl(context, stackTrace.targetException(context)),
                    readyInternal = true
                )
                var receiverChannel = ExceptionPreconditionChannel(
                    "${index++}",
                    preconditionBuilder(),
                    readyInternal = false
                )
                var targetInstructions = stackTrace.targetInstructions(context)

                val stackTraceLines = mutableListOf<StackTraceElement>()
                val allJobs = stackTrace.stackTraceLines
                    .zip(stackTrace.stackTraceLines.drop(1) + null)
                    .mapTo(mutableListOf()) { (line, next) ->
                        stackTraceLines += line
                        val currentStackTrace = StackTrace(stackTrace.firstLine, stackTraceLines.toList())
                        val checker = crashReproductionBuilder(
                            context,
                            currentStackTrace,
                            targetInstructions,
                            producerChannel,
                            receiverChannel,
                            ExceptionReproductionCheckerImpl(context, currentStackTrace)
                        )
                        checkers += checker
                        producerChannel = receiverChannel
                        receiverChannel = ExceptionPreconditionChannel(
                            "${index++}",
                            preconditionBuilder(),
                            readyInternal = false
                        )
                        next?.let {
                            targetInstructions = context.cm[next].body.flatten()
                                .filter { it.location.line == next.lineNumber }
                                .filterTo(mutableSetOf()) { it is CallInst && it.method.name == line.methodName }
                        }
                        async { checker.analyze() }
                    }
                allJobs += async {
                    while (!producerChannel.ready) {
                        delay(500.milliseconds)
                    }
                    producerChannel.stopProviding()
                }
                allJobs.awaitAll()
            }
            val reproductionChecker = ExceptionReproductionCheckerImpl(context, stackTrace)
            val filteredTestCases = checkers.last().result.preconditions.keys.filterTo(mutableSetOf()) {
                reproductionChecker.isReproduced(it)
            }
            val testCasePaths = filteredTestCases.mapTo(mutableSetOf()) {
                kexConfig.testcaseDirectory.resolve("${it.asmString}.java").toAbsolutePath().normalize()
            }
            kexConfig.testcaseDirectory
                .takeIf { Files.exists(it) }
                ?.let { testCaseDir ->
                    Files.walk(testCaseDir)
                        .filter { !it.isDirectory() }
                        .filter { it !in testCasePaths }
                        .filter { !it.endsWith("${ReflectionUtilsPrinter.REFLECTION_UTILS_CLASS}.java") }
                        .forEach { Files.deleteIfExists(it) }
                }
            filteredTestCases
        }
    }
}
