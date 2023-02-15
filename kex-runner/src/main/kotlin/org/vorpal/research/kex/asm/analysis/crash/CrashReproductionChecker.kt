package org.vorpal.research.kex.asm.analysis.crash

import kotlinx.coroutines.*
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.*
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
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.*
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

class CrashReproductionChecker(
    ctx: ExecutionContext,
    @Suppress("MemberVisibilityCanBePrivate")
    val stackTrace: StackTrace,
    private val shouldStopAfterFirst: Boolean
) : SymbolicTraverser(ctx, ctx.cm[stackTrace.stackTraceLines.last()]) {
    override val pathSelector: SymbolicPathSelector = DequePathSelector()
    override val callResolver: SymbolicCallResolver = StackTraceCallResolver(
        stackTrace, DefaultCallResolver(ctx)
    )
    override val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)


    private val targetException = ctx.cm[stackTrace.firstLine.takeWhile { it != ':' }.asmString]
    private val targetInstructions = ctx.cm[stackTrace.stackTraceLines.first()].body.flatten()
        .filter { it.location.line == stackTrace.stackTraceLines.first().lineNumber }
        .filterTo(mutableSetOf()) {
            when (targetException) {
                nullptrClass -> it.isNullptrThrowing
                arrayIndexOOBClass -> it is ArrayStoreInst || it is ArrayLoadInst
                negativeArrayClass -> it is NewArrayInst
                classCastClass -> it is CastInst
                else -> it is ThrowInst
            }
        }

    private var foundAnExample = false
    private val generatedTestClasses = mutableSetOf<String>()

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

            val coroutineContext = newFixedThreadPoolContextWithMDC(1, "crash-dispatcher")
            return runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit.seconds) {
                    async {
                        val checker = CrashReproductionChecker(context, stackTrace, stopAfterFirstCrash)
                        checker.analyze()
                        checker.generatedTestClasses
                    }.await()
                } ?: emptySet()
            }
        }
    }

    override suspend fun traverseInstruction(inst: Instruction) {
        if (shouldStopAfterFirst && foundAnExample) {
            return
        }

        super.traverseInstruction(inst)
    }

    override suspend fun nullabilityCheck(
        state: TraverserState,
        inst: Instruction,
        term: Term
    ): TraverserState? = when {
        inst in targetInstructions && targetException == nullptrClass -> {
            val checkResult = super.nullabilityCheck(state, inst, term)
            targetInstructions -= inst
            when {
                targetInstructions.isEmpty() -> null
                else -> checkResult
            }
        }

        else -> {
            val persistentState = state.symbolicState
            val nullityClause = PathClause(
                PathClauseType.NULL_CHECK,
                inst,
                path { (term eq null) equality false }
            )
            checkReachability(
                state.copy(
                    symbolicState = persistentState.copy(
                        path = persistentState.path.add(nullityClause)
                    ),
                    nullCheckedTerms = state.nullCheckedTerms.add(term)
                ), inst
            )
        }
    }

    override suspend fun boundsCheck(
        state: TraverserState,
        inst: Instruction,
        index: Term,
        length: Term
    ): TraverserState? = when {
        inst in targetInstructions && targetException == arrayIndexOOBClass -> {
            val checkResult = super.boundsCheck(state, inst, index, length)
            targetInstructions -= inst
            when {
                targetInstructions.isEmpty() -> null
                else -> checkResult
            }
        }

        else -> {
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
            checkReachability(
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
    }

    override suspend fun newArrayBoundsCheck(
        state: TraverserState,
        inst: Instruction,
        index: Term
    ): TraverserState? = when {
        inst in targetInstructions && targetException == negativeArrayClass -> {
            val checkResult = super.newArrayBoundsCheck(state, inst, index)
            targetInstructions -= inst
            when {
                targetInstructions.isEmpty() -> null
                else -> checkResult
            }
        }

        else -> {
            val persistentState = state.symbolicState
            val zeroClause = PathClause(
                PathClauseType.BOUNDS_CHECK,
                inst,
                path { (index ge 0) equality true }
            )
            checkReachability(
                state.copy(
                    symbolicState = persistentState.copy(
                        path = persistentState.path.add(zeroClause)
                    ),
                    boundCheckedTerms = state.boundCheckedTerms.add(index to index)
                ), inst
            )
        }
    }

    override suspend fun typeCheck(
        state: TraverserState,
        inst: Instruction,
        term: Term,
        type: KexType
    ): TraverserState? = when {
        inst in targetInstructions && targetException == classCastClass -> {
            val checkResult = super.typeCheck(state, inst, term, type)
            targetInstructions -= inst
            when {
                targetInstructions.isEmpty() -> null
                else -> checkResult
            }
        }

        else -> {
            val currentlyCheckedType = type.getKfgType(ctx.types)
            val persistentState = state.symbolicState
            val typeClause = PathClause(
                PathClauseType.TYPE_CHECK,
                inst,
                path { (term `is` type) equality true }
            )
            checkReachability(
                state.copy(
                    symbolicState = persistentState.copy(
                        path = persistentState.path.add(typeClause)
                    ),
                    typeCheckedTerms = state.typeCheckedTerms.put(term, currentlyCheckedType)
                ), inst
            )
        }
    }

    override fun report(inst: Instruction, parameters: Parameters<Descriptor>, testPostfix: String) {
        if (inst in targetInstructions) {
            foundAnExample = true
            val testName = rootMethod.klassName + testPostfix + testIndex.getAndIncrement()
            val generator = UnsafeGenerator(ctx, rootMethod, testName)
            generator.generate(parameters)
            val testFile = generator.emit()
            try {
                compilerHelper.compileFile(testFile)
                generatedTestClasses += generator.testKlassName
            } catch (e: CompilationException) {
                log.error("Failed to compile test file $testFile")
            }
        }
    }

    private val Instruction.isNullptrThrowing
        get() = when (this) {
            is ArrayLoadInst -> true
            is ArrayStoreInst -> true
            is FieldLoadInst -> true
            is FieldStoreInst -> true
            is CallInst -> !this.isStatic
            else -> false
        }
}
