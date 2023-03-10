package org.vorpal.research.kex.asm.analysis.crash

import kotlinx.coroutines.*
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.*
import org.vorpal.research.kex.compile.CompilationException
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kex.util.asList
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.ThisRef
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
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
    private val targetException = ctx.cm[stackTrace.firstLine.takeWhile { it != ':' }.asmString]
    private val targetInstructions = ctx.cm[stackTrace.stackTraceLines.first()].body.flatten()
        .filter { it.location.line == stackTrace.stackTraceLines.first().lineNumber }
        .filterTo(mutableSetOf()) {
            when (targetException) {
                nullptrClass -> it.isNullptrThrowing
                arrayIndexOOBClass -> it is ArrayStoreInst || it is ArrayLoadInst
                negativeArrayClass -> it is NewArrayInst
                classCastClass -> it is CastInst
                else -> when (it) {
                    is ThrowInst -> it.throwable.type == targetException.asType
                    is CallInst -> targetException.isInheritorOf(cm["java/lang/RuntimeException"]) || targetException in it.method.exceptions
                    else -> false
                }
            }
        }

    override val pathSelector: SymbolicPathSelector = RandomizedDistancePathSelector(ctx, rootMethod, targetInstructions, stackTrace)
    override val callResolver: SymbolicCallResolver = StackTraceCallResolver(
        stackTrace, DefaultCallResolver(ctx)
    )
    override val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)

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

        if (inst in targetInstructions) {
            val state = currentState ?: return
            for (preCondition in generatePreCondition(inst, state)) {
                val triggerState = state.copy(
                    symbolicState = state.symbolicState + preCondition
                )
                checkExceptionAndReport(triggerState, inst, generate(targetException.symbolicClass))
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
            is ArrayLoadInst -> this.arrayRef !is ThisRef
            is ArrayStoreInst -> this.arrayRef !is ThisRef
            is FieldLoadInst -> !this.isStatic && this.owner !is ThisRef
            is FieldStoreInst -> !this.isStatic && this.owner !is ThisRef
            is CallInst -> !this.isStatic && this.callee !is ThisRef
            else -> false
        }


    private fun generatePreCondition(targetInst: Instruction, state: TraverserState): List<PersistentSymbolicState> =
        when (targetException) {
            nullptrClass -> persistentSymbolicState(
                path = when (targetInst) {
                    is ArrayLoadInst -> persistentPathConditionOf(
                        PathClause(PathClauseType.NULL_CHECK, targetInst, path {
                            (state.mkTerm(targetInst.arrayRef) eq null) equality true
                        })
                    )

                    is ArrayStoreInst -> persistentPathConditionOf(
                        PathClause(PathClauseType.NULL_CHECK, targetInst, path {
                            (state.mkTerm(targetInst.arrayRef) eq null) equality true
                        })
                    )

                    is FieldLoadInst -> when {
                        targetInst.isStatic -> persistentPathConditionOf()
                        else -> persistentPathConditionOf(
                            PathClause(PathClauseType.NULL_CHECK, targetInst, path {
                                (state.mkTerm(targetInst.owner) eq null) equality true
                            })
                        )
                    }

                    is FieldStoreInst -> when {
                        targetInst.isStatic -> persistentPathConditionOf()
                        else -> persistentPathConditionOf(
                            PathClause(PathClauseType.NULL_CHECK, targetInst, path {
                                (state.mkTerm(targetInst.owner) eq null) equality true
                            })
                        )
                    }

                    is CallInst -> when {
                        targetInst.isStatic -> persistentPathConditionOf()
                        else -> persistentPathConditionOf(
                            PathClause(PathClauseType.NULL_CHECK, targetInst, path {
                                (state.mkTerm(targetInst.callee) eq null) equality true
                            })
                        )
                    }

                    else -> unreachable { log.error("Instruction ${targetInst.print()} does not throw null pointer") }
                }
            ).asList()

            arrayIndexOOBClass -> {
                val (arrayTerm, indexTerm) = when (targetInst) {
                    is ArrayLoadInst -> state.mkTerm(targetInst.arrayRef) to state.mkTerm(targetInst.index)
                    is ArrayStoreInst -> state.mkTerm(targetInst.arrayRef) to state.mkTerm(targetInst.index)
                    else -> unreachable { log.error("Instruction ${targetInst.print()} does not throw array index out of bounds") }
                }
                listOf(
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, targetInst, path {
                                (indexTerm ge 0) equality false
                            })
                        )
                    ),
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, targetInst, path {
                                (indexTerm lt arrayTerm.length()) equality false
                            }),
                        )
                    )
                )
            }

            negativeArrayClass -> when (targetInst) {
                is NewArrayInst -> targetInst.dimensions.map { length ->
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, targetInst, path {
                                (state.mkTerm(length) ge 0) equality false
                            }),
                        )
                    )
                }

                else -> unreachable { log.error("Instruction ${targetInst.print()} does not throw negative array size") }
            }

            classCastClass -> when (targetInst) {
                is CastInst -> listOf(
                    persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(PathClauseType.BOUNDS_CHECK, targetInst, path {
                                (state.mkTerm(targetInst.operand) `is` targetInst.type.kexType) equality false
                            }),
                        )
                    )
                )

                else -> unreachable { log.error("Instruction ${targetInst.print()} does not throw class cast") }
            }

            else -> when (targetInst) {
                is ThrowInst -> when (targetInst.throwable.type) {
                    targetException.asType -> persistentSymbolicState().asList()
                    else -> emptyList()
                }

                is CallInst -> {
                    persistentSymbolicState().asList()
                }

                else -> unreachable { log.error("Instruction ${targetInst.print()} does not throw class cast") }
            }
        }
}
