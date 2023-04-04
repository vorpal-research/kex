package org.vorpal.research.kex.asm.analysis.crash

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.DefaultCallResolver
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicCallResolver
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicInvokeDynamicResolver
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicPathSelector
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicTraverser
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.compile.CompilationException
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.util.arrayIndexOOBClass
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.classCastClass
import org.vorpal.research.kex.util.negativeArrayClass
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kex.util.nullptrClass
import org.vorpal.research.kex.util.runtimeException
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.ThisRef
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


operator fun ClassManager.get(frame: StackTraceElement): Method {
    val entryClass = this[frame.className.asmString]
    return entryClass.allMethods.filter { it.name == frame.methodName }.first { method ->
        method.body.flatten().any { inst ->
            inst.location.file == frame.fileName && inst.location.line == frame.lineNumber
        }
    }
}

private val Instruction.isNullptrThrowing
    get() = when (this) {
        is ArrayLoadInst -> this.arrayRef !is ThisRef
        is ArrayStoreInst -> this.arrayRef !is ThisRef
        is FieldLoadInst -> !this.isStatic && this.owner !is ThisRef
        is FieldStoreInst -> !this.isStatic && this.owner !is ThisRef
        is CallInst -> !this.isStatic && this.callee !is ThisRef
        else -> false
    }

class CrashReproductionChecker(
    ctx: ExecutionContext,
    @Suppress("MemberVisibilityCanBePrivate")
    val stackTrace: StackTrace,
    private val targetInstructions: Set<Instruction>,
    private val preconditionBuilder: ExceptionPreConditionBuilder,
    private val reproductionChecker: TestKlassReproductionChecker,
    private val shouldStopAfterFirst: Boolean
) : SymbolicTraverser(ctx, ctx.cm[stackTrace.stackTraceLines.last()]) {
    override val pathSelector: SymbolicPathSelector =
        RandomizedDistancePathSelector(ctx, rootMethod, targetInstructions, stackTrace)
    override val callResolver: SymbolicCallResolver = StackTraceCallResolver(
        stackTrace, DefaultCallResolver(ctx)
    )
    override val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)

    private var foundAnExample = false
    private val generatedTestClasses = mutableSetOf<String>()
    private val descriptors = mutableMapOf<String, Parameters<Descriptor>>()

    init {
        ktassert(
            targetInstructions.isNotEmpty(),
            "Could not find target instructions for stack trace\n\"\"\"${stackTrace.originalStackTrace}\"\"\""
        )
    }

    companion object {
        @ExperimentalTime
        @DelicateCoroutinesApi
        fun run(context: ExecutionContext, stackTrace: StackTrace): Set<String> {
            val timeLimit = kexConfig.getIntValue("crash", "timeLimit", 100)
            val stopAfterFirstCrash = kexConfig.getBooleanValue("crash", "stopAfterFirstCrash", false)

            val targetException = context.cm[stackTrace.firstLine.takeWhile { it != ':' }.asmString]
            val targetInstructions = context.cm[stackTrace.stackTraceLines.first()].body.flatten()
                .filter { it.location.line == stackTrace.stackTraceLines.first().lineNumber }
                .filterTo(mutableSetOf()) {
                    when (targetException) {
                        context.cm.nullptrClass -> it.isNullptrThrowing
                        context.cm.arrayIndexOOBClass -> it is ArrayStoreInst || it is ArrayLoadInst
                        context.cm.negativeArrayClass -> it is NewArrayInst
                        context.cm.classCastClass -> it is CastInst
                        else -> when (it) {
                            is ThrowInst -> it.throwable.type == targetException.asType
                            is CallInst -> targetException.isInheritorOf(context.cm.runtimeException)
                                    || targetException in it.method.exceptions

                            else -> false
                        }
                    }
                }

            val coroutineContext = newFixedThreadPoolContextWithMDC(1, "crash-dispatcher")
            return runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit.seconds) {
                    async {
                        val checker = CrashReproductionChecker(
                            context,
                            stackTrace,
                            targetInstructions,
                            ExceptionPreConditionBuilderImpl(context, targetException),
                            TestKlassReproductionCheckerImpl(context, stackTrace),
                            stopAfterFirstCrash
                        )
                        checker.analyze()
                        checker.generatedTestClasses
                    }.await()
                } ?: emptySet()
            }
        }

        @ExperimentalTime
        @DelicateCoroutinesApi
        fun runIteratively(context: ExecutionContext, stackTrace: StackTrace): Set<String> {
            val timeLimit = kexConfig.getIntValue("crash", "timeLimit", 100)
            val stopAfterFirstCrash = kexConfig.getBooleanValue("crash", "stopAfterFirstCrash", false)

            val targetException = context.cm[stackTrace.firstLine.takeWhile { it != ':' }.asmString]
            val targetInstructions = context.cm[stackTrace.stackTraceLines.first()].body.flatten()
                .filter { it.location.line == stackTrace.stackTraceLines.first().lineNumber }
                .filterTo(mutableSetOf()) {
                    when (targetException) {
                        context.cm.nullptrClass -> it.isNullptrThrowing
                        context.cm.arrayIndexOOBClass -> it is ArrayStoreInst || it is ArrayLoadInst
                        context.cm.negativeArrayClass -> it is NewArrayInst
                        context.cm.classCastClass -> it is CastInst
                        else -> when (it) {
                            is ThrowInst -> it.throwable.type == targetException.asType
                            is CallInst -> targetException.isInheritorOf(context.cm.runtimeException)
                                    || targetException in it.method.exceptions

                            else -> false
                        }
                    }
                }
            val coroutineContext = newFixedThreadPoolContextWithMDC(1, "crash-dispatcher")

            val firstLine = stackTrace.stackTraceLines.first()
            var descriptors = runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit.seconds) {
                    async {
                        val checker = CrashReproductionChecker(
                            context,
                            stackTrace,
                            targetInstructions,
                            ExceptionPreConditionBuilderImpl(context, targetException),
                            TestKlassReproductionCheckerImpl(
                                context,
                                StackTrace(stackTrace.firstLine, listOf(firstLine))
                            ),
                            stopAfterFirstCrash
                        )
                        checker.analyze()
                        checker.generatedTestClasses.associateWith { checker.descriptors[it]!! }
                    }.await()
                } ?: emptyMap()
            }
            for (line in stackTrace.stackTraceLines.drop(1)) {
                if (descriptors.isEmpty()) break

                descriptors = runBlocking(coroutineContext) {
                    withTimeoutOrNull(timeLimit.seconds) {
                        async {
                            val checker = CrashReproductionChecker(
                                context,
                                stackTrace,
                                targetInstructions,
                                DescriptorPreconditionBuilder(context, targetException, descriptors.values.toSet()),
                                TestKlassReproductionCheckerImpl(
                                    context,
                                    StackTrace(stackTrace.firstLine, listOf(firstLine))
                                ),
                                stopAfterFirstCrash
                            )
                            checker.analyze()
                            checker.generatedTestClasses.associateWith { checker.descriptors[it]!! }
                        }.await()
                    } ?: emptyMap()
                }
            }

            return descriptors.keys
        }
    }

    override suspend fun traverseInstruction(inst: Instruction) {
        if (shouldStopAfterFirst && foundAnExample) {
            return
        }

        if (inst in targetInstructions) {
            val state = currentState ?: return
            for (preCondition in preconditionBuilder.build(inst, state)) {
                val triggerState = state.copy(
                    symbolicState = state.symbolicState + preCondition
                )
                checkExceptionAndReport(triggerState, inst, generate(preconditionBuilder.targetException.symbolicClass))
            }
        }

        super.traverseInstruction(inst)
    }

    override suspend fun nullabilityCheck(
        state: TraverserState,
        inst: Instruction,
        term: Term
    ): TraverserState? {
        val persistentState = state.symbolicState
        val nullityClause = PathClause(
            PathClauseType.NULL_CHECK,
            inst,
            path { (term eq null) equality false }
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(nullityClause)
                ),
                nullCheckedTerms = state.nullCheckedTerms.add(term)
            ), inst
        )
    }

    override suspend fun boundsCheck(
        state: TraverserState,
        inst: Instruction,
        index: Term,
        length: Term
    ): TraverserState? {

        val persistentState = state.symbolicState
        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality true }
        )
        val lengthClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index lt length) equality true }
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(
                        zeroClause.copy(predicate = zeroClause.predicate)
                    ).add(
                        lengthClause.copy(predicate = lengthClause.predicate)
                    )
                ),
                boundCheckedTerms = state.boundCheckedTerms.add(index to length)
            ), inst
        )
    }

    override suspend fun newArrayBoundsCheck(
        state: TraverserState,
        inst: Instruction,
        index: Term
    ): TraverserState? {
        val persistentState = state.symbolicState
        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality true }
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(zeroClause)
                ),
                boundCheckedTerms = state.boundCheckedTerms.add(index to index)
            ), inst
        )
    }

    override suspend fun typeCheck(
        state: TraverserState,
        inst: Instruction,
        term: Term,
        type: KexType
    ): TraverserState? {
        val currentlyCheckedType = type.getKfgType(ctx.types)
        val persistentState = state.symbolicState
        val typeClause = PathClause(
            PathClauseType.TYPE_CHECK,
            inst,
            path { (term `is` type) equality true }
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(typeClause)
                ),
                typeCheckedTerms = state.typeCheckedTerms.put(term, currentlyCheckedType)
            ), inst
        )
    }

    override fun report(inst: Instruction, parameters: Parameters<Descriptor>, testPostfix: String): Boolean {
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
            foundAnExample = true
            generatedTestClasses += generator.testKlassName
            descriptors[generator.testKlassName] = parameters
            return true
        } catch (e: CompilationException) {
            log.error("Failed to compile test file $testFile")
            return false
        }
    }
}
