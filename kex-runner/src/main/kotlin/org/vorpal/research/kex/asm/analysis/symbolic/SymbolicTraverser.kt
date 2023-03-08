package org.vorpal.research.kex.asm.analysis.symbolic

import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.util.analyzeOrTimeout
import org.vorpal.research.kex.asm.analysis.util.checkAsync
import org.vorpal.research.kex.compile.CompilationException
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.state.predicate.inverse
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.ValueFactory
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.util.concurrent.atomic.AtomicInteger

data class SymbolicStackTraceElement(
    val method: Method,
    val instruction: Instruction,
    val valueMap: PersistentMap<Value, Term>
)

data class TraverserState(
    val symbolicState: PersistentSymbolicState,
    val valueMap: PersistentMap<Value, Term>,
    val stackTrace: PersistentList<SymbolicStackTraceElement>,
    val typeInfo: PersistentMap<Term, Type>,
    val blockPath: PersistentList<BasicBlock>,
    val nullCheckedTerms: PersistentSet<Term>,
    val boundCheckedTerms: PersistentSet<Pair<Term, Term>>,
    val typeCheckedTerms: PersistentMap<Term, Type>
) {
    fun mkTerm(value: Value): Term = when (value) {
        is Constant -> term { const(value) }
        else -> valueMap.getValue(value)
    }
}

@Suppress("RedundantSuspendModifier", "MemberVisibilityCanBePrivate")
abstract class SymbolicTraverser(
    val ctx: ExecutionContext,
    val rootMethod: Method,
) : TermBuilder() {
    val cm: ClassManager
        get() = ctx.cm
    val types: TypeFactory
        get() = ctx.types
    val values: ValueFactory
        get() = ctx.values

    abstract val pathSelector: SymbolicPathSelector
    abstract val callResolver: SymbolicCallResolver
    abstract val invokeDynamicResolver: SymbolicInvokeDynamicResolver

    protected var currentState: TraverserState? = null
    protected var testIndex = AtomicInteger(0)
    protected val compilerHelper = CompilerHelper(ctx)

    protected val nullptrClass = cm["java/lang/NullPointerException"]
    protected val arrayIndexOOBClass = cm["java/lang/ArrayIndexOutOfBoundsException"]
    protected val negativeArrayClass = cm["java/lang/NegativeArraySizeException"]
    protected val classCastClass = cm["java/lang/ClassCastException"]

    protected val Type.symbolicType: KexType get() = kexType.rtMapped
    protected val org.vorpal.research.kfg.ir.Class.symbolicClass: KexType get() = kexType.rtMapped

    suspend fun analyze() = rootMethod.analyzeOrTimeout(ctx.accessLevel) {
        processMethod(it)
    }

    protected open suspend fun processMethod(method: Method) {
        val thisValue = values.getThis(method.klass)
        val initialArguments = buildMap {
            val values = this@SymbolicTraverser.values
            if (!method.isStatic) {
                this[thisValue] = `this`(method.klass.symbolicClass)
            }
            for ((index, type) in method.argTypes.withIndex()) {
                this[values.getArgument(index, method, type)] = arg(type.symbolicType, index)
            }
        }

        val initialState = when {
            !method.isStatic -> {
                val thisTerm = initialArguments[thisValue]!!
                val thisType = method.klass.symbolicClass.getKfgType(types)
                TraverserState(
                    symbolicState = persistentSymbolicState(
                        path = persistentPathConditionOf(
                            PathClause(
                                PathClauseType.NULL_CHECK,
                                method.body.entry.first(),
                                path { (thisTerm eq null) equality false }
                            )
                        )
                    ),
                    valueMap = initialArguments.toPersistentMap(),
                    stackTrace = persistentListOf(),
                    typeInfo = persistentMapOf(thisTerm to thisType),
                    blockPath = persistentListOf(),
                    nullCheckedTerms = persistentSetOf(thisTerm),
                    boundCheckedTerms = persistentSetOf(),
                    typeCheckedTerms = persistentMapOf(thisTerm to thisType)
                )
            }

            else -> TraverserState(
                symbolicState = persistentSymbolicState(),
                valueMap = initialArguments.toPersistentMap(),
                stackTrace = persistentListOf(),
                typeInfo = persistentMapOf(),
                blockPath = persistentListOf(),
                nullCheckedTerms = persistentSetOf(),
                boundCheckedTerms = persistentSetOf(),
                typeCheckedTerms = persistentMapOf()
            )
        }

        pathSelector.add(initialState, method.body.entry)

        while (pathSelector.hasNext()) {
            val (currentState, currentBlock) = pathSelector.next()
            this.currentState = currentState
            traverseBlock(currentBlock)
            yield()
        }
    }

    protected open suspend fun traverseBlock(bb: BasicBlock, startIndex: Int = 0) {
        for (index in startIndex..bb.instructions.lastIndex) {
            val inst = bb.instructions[index]
            traverseInstruction(inst)
        }
    }


    protected open suspend fun traverseInstruction(inst: Instruction) {
        when (inst) {
            is ArrayLoadInst -> traverseArrayLoadInst(inst)
            is ArrayStoreInst -> traverseArrayStoreInst(inst)
            is BinaryInst -> traverseBinaryInst(inst)
            is CallInst -> traverseCallInst(inst)
            is CastInst -> traverseCastInst(inst)
            is CatchInst -> traverseCatchInst(inst)
            is CmpInst -> traverseCmpInst(inst)
            is EnterMonitorInst -> traverseEnterMonitorInst(inst)
            is ExitMonitorInst -> traverseExitMonitorInst(inst)
            is FieldLoadInst -> traverseFieldLoadInst(inst)
            is FieldStoreInst -> traverseFieldStoreInst(inst)
            is InstanceOfInst -> traverseInstanceOfInst(inst)
            is InvokeDynamicInst -> traverseInvokeDynamicInst(inst)
            is NewArrayInst -> traverseNewArrayInst(inst)
            is NewInst -> traverseNewInst(inst)
            is PhiInst -> traversePhiInst(inst)
            is UnaryInst -> traverseUnaryInst(inst)
            is BranchInst -> traverseBranchInst(inst)
            is JumpInst -> traverseJumpInst(inst)
            is ReturnInst -> traverseReturnInst(inst)
            is SwitchInst -> traverseSwitchInst(inst)
            is TableSwitchInst -> traverseTableSwitchInst(inst)
            is ThrowInst -> traverseThrowInst(inst)
            is UnreachableInst -> traverseUnreachableInst(inst)
            is UnknownValueInst -> traverseUnknownValueInst(inst)
            else -> unreachable("Unknown instruction ${inst.print()}")
        }
    }

    private fun nullableCurrentState() {
        currentState = null
    }

    protected open suspend fun traverseArrayLoadInst(inst: ArrayLoadInst) {
        var traverserState = currentState ?: return

        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val res = generate(inst.type.symbolicType)

        traverserState = nullabilityCheck(traverserState, inst, arrayTerm) ?: return nullableCurrentState()
        traverserState =
            boundsCheck(traverserState, inst, indexTerm, arrayTerm.length()) ?: return nullableCurrentState()

        val clause = StateClause(inst, state { res equality arrayTerm[indexTerm].load() })
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, res)
            ), inst
        )
    }

    protected open suspend fun traverseArrayStoreInst(inst: ArrayStoreInst) {
        var traverserState = currentState ?: return

        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val valueTerm = traverserState.mkTerm(inst.value)

        traverserState = nullabilityCheck(traverserState, inst, arrayTerm) ?: return nullableCurrentState()
        traverserState =
            boundsCheck(traverserState, inst, indexTerm, arrayTerm.length()) ?: return nullableCurrentState()

        val clause = StateClause(inst, state { arrayTerm[indexTerm].store(valueTerm) })
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                )
            ), inst
        )
    }

    protected open suspend fun traverseBinaryInst(inst: BinaryInst) {
        val traverserState = currentState ?: return

        val lhvTerm = traverserState.mkTerm(inst.lhv)
        val rhvTerm = traverserState.mkTerm(inst.rhv)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality lhvTerm.apply(resultTerm.type, inst.opcode, rhvTerm) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            ), inst
        )
    }

    protected open suspend fun traverseBranchInst(inst: BranchInst) {
        val traverserState = currentState ?: return
        val condTerm = traverserState.mkTerm(inst.cond)

        val trueClause = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { condTerm equality true }
        )
        val falseClause = trueClause.copy(predicate = trueClause.predicate.inverse(ctx.random))

        checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    path = traverserState.symbolicState.path.add(trueClause)
                ),
                blockPath = traverserState.blockPath.add(inst.parent)
            ), inst
        )?.let {
            pathSelector += it to inst.trueSuccessor
        }

        checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    path = traverserState.symbolicState.path.add(falseClause)
                ),
                blockPath = traverserState.blockPath.add(inst.parent)
            ), inst
        )?.let {
            pathSelector += it to inst.falseSuccessor
        }

        currentState = null
    }

    protected open suspend fun traverseCallInst(inst: CallInst) {
        var traverserState = currentState ?: return

        val callee = when {
            inst.isStatic -> staticRef(inst.method.klass)
            else -> traverserState.mkTerm(inst.callee)
        }
        val argumentTerms = inst.args.map { traverserState.mkTerm(it) }
        if (!inst.isStatic) {
            traverserState = nullabilityCheck(traverserState, inst, callee) ?: return nullableCurrentState()
        }

        val candidates = callResolver.resolve(traverserState, inst)
        for (candidate in candidates) {
            processMethodCall(traverserState, inst, candidate, callee, argumentTerms)
        }
        currentState = when {
            candidates.isEmpty() -> {
                val receiver = when {
                    inst.isNameDefined -> {
                        val res = generate(inst.type.symbolicType)
                        traverserState = traverserState.copy(
                            valueMap = traverserState.valueMap.put(inst, res)
                        )
                        res
                    }

                    else -> null
                }
                val callClause = StateClause(
                    inst, state {
                        val callTerm = callee.call(inst.method, argumentTerms)
                        receiver?.call(callTerm) ?: call(callTerm)
                    }
                )
                checkReachability(
                    traverserState.copy(
                        symbolicState = traverserState.symbolicState.copy(
                            clauses = traverserState.symbolicState.clauses.add(callClause)
                        )
                    ), inst
                )
            }

            else -> null
        }
    }

    protected open suspend fun traverseCastInst(inst: CastInst) {
        var traverserState = currentState ?: return

        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)

        if (operandTerm.type is KexPointer) {
            traverserState = typeCheck(traverserState, inst, operandTerm, resultTerm.type)
                ?: return nullableCurrentState()
        }
        val clause = StateClause(
            inst,
            state { resultTerm equality (operandTerm `as` resultTerm.type) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            ), inst
        )
    }

    @Suppress("UNUSED_PARAMETER")
    protected open suspend fun traverseCatchInst(inst: CatchInst) {
    }

    protected open suspend fun traverseCmpInst(inst: CmpInst) {
        val traverserState = currentState ?: return

        val lhvTerm = traverserState.mkTerm(inst.lhv)
        val rhvTerm = traverserState.mkTerm(inst.rhv)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality lhvTerm.apply(inst.opcode, rhvTerm) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            ), inst
        )
    }

    protected open suspend fun traverseEnterMonitorInst(inst: EnterMonitorInst) {
        var traverserState = currentState ?: return
        val monitorTerm = traverserState.mkTerm(inst.owner)

        traverserState = nullabilityCheck(traverserState, inst, monitorTerm) ?: return nullableCurrentState()
        val clause = StateClause(
            inst,
            state { enterMonitor(monitorTerm) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                )
            ), inst
        )
    }

    protected open suspend fun traverseExitMonitorInst(inst: ExitMonitorInst) {
        val traverserState = currentState ?: return

        val monitorTerm = traverserState.mkTerm(inst.owner)

        val clause = StateClause(
            inst,
            state { exitMonitor(monitorTerm) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                )
            ), inst
        )
    }

    protected open suspend fun traverseFieldLoadInst(inst: FieldLoadInst) {
        var traverserState = currentState ?: return

        val field = inst.field
        val objectTerm = when {
            inst.isStatic -> staticRef(field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }
        val res = generate(inst.type.symbolicType)

        traverserState = nullabilityCheck(traverserState, inst, objectTerm) ?: return nullableCurrentState()

        val clause = StateClause(
            inst,
            state { res equality objectTerm.field(field.type.symbolicType, field.name).load() }
        )
        val newNullChecked = when {
            field.isStatic && field.isFinal -> when (field.defaultValue) {
                null -> traverserState.nullCheckedTerms.add(res)
                ctx.values.nullConstant -> traverserState.nullCheckedTerms
                else -> traverserState.nullCheckedTerms.add(res)
            }

            else -> traverserState.nullCheckedTerms
        }
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, res),
                nullCheckedTerms = newNullChecked
            ), inst
        )
    }

    protected open suspend fun traverseFieldStoreInst(inst: FieldStoreInst) {
        var traverserState = currentState ?: return

        val objectTerm = when {
            inst.isStatic -> staticRef(inst.field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }
        val valueTerm = traverserState.mkTerm(inst.value)

        traverserState = nullabilityCheck(traverserState, inst, objectTerm) ?: return nullableCurrentState()

        val clause = StateClause(
            inst,
            state { objectTerm.field(inst.field.type.symbolicType, inst.field.name).store(valueTerm) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, valueTerm)
            ), inst
        )
    }

    protected open suspend fun traverseInstanceOfInst(inst: InstanceOfInst) {
        val traverserState = currentState ?: return
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality (operandTerm `is` inst.targetType.symbolicType) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            ), inst
        )
    }

    protected open suspend fun traverseInvokeDynamicInst(inst: InvokeDynamicInst) {
        val traverserState = currentState ?: return
        currentState = invokeDynamicResolver.resolve(traverserState, inst) ?: return
    }

    protected open suspend fun processMethodCall(
        state: TraverserState,
        inst: Instruction,
        candidate: Method,
        callee: Term,
        argumentTerms: List<Term>
    ) {
        if (candidate.body.isEmpty()) return
        var traverserState = state
        var newValueMap = traverserState.valueMap.builder().let { builder ->
            if (!candidate.isStatic) builder[values.getThis(candidate.klass)] = callee
            for ((index, type) in candidate.argTypes.withIndex()) {
                builder[values.getArgument(index, candidate, type)] = argumentTerms[index]
            }
            builder.build()
        }
        if (!candidate.isStatic) {
            traverserState =
                typeCheck(traverserState, inst, callee, candidate.klass.symbolicClass) ?: return nullableCurrentState()
            if (candidate.klass.type != callee.type.getKfgType(types)) {
                val newCalleeTerm = generate(candidate.klass.symbolicClass)
                val convertClause = StateClause(inst, state {
                    newCalleeTerm equality (callee `as` candidate.klass.symbolicClass)
                })
                traverserState = traverserState.copy(
                    symbolicState = traverserState.symbolicState.copy(
                        clauses = traverserState.symbolicState.clauses.add(convertClause)
                    )
                )
                newValueMap = newValueMap.mapValues { (_, term) ->
                    when (term) {
                        callee -> newCalleeTerm
                        else -> term
                    }
                }.toPersistentMap()
            }
        }
        val newState = traverserState.copy(
            symbolicState = traverserState.symbolicState,
            valueMap = newValueMap,
            stackTrace = traverserState.stackTrace.add(
                SymbolicStackTraceElement(inst.parent.method, inst, traverserState.valueMap)
            )
        )
        pathSelector.add(
            newState, candidate.body.entry
        )
    }

    protected open suspend fun traverseNewArrayInst(inst: NewArrayInst) {
        var traverserState = currentState ?: return

        val dimensions = inst.dimensions.map { traverserState.mkTerm(it) }
        val resultTerm = generate(inst.type.symbolicType)

        dimensions.forEach {
            traverserState = newArrayBoundsCheck(traverserState, inst, it) ?: return nullableCurrentState()
        }
        val clause = StateClause(
            inst,
            state { resultTerm.new(dimensions) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                typeInfo = traverserState.typeInfo.put(resultTerm, inst.type.rtMapped),
                valueMap = traverserState.valueMap.put(inst, resultTerm),
                nullCheckedTerms = traverserState.nullCheckedTerms.add(resultTerm),
                typeCheckedTerms = traverserState.typeCheckedTerms.put(resultTerm, inst.type)
            ), inst
        )
    }

    protected open suspend fun traverseNewInst(inst: NewInst) {
        val traverserState = currentState ?: return
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm.new() }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                typeInfo = traverserState.typeInfo.put(resultTerm, inst.type.rtMapped),
                valueMap = traverserState.valueMap.put(inst, resultTerm),
                nullCheckedTerms = traverserState.nullCheckedTerms.add(resultTerm),
                typeCheckedTerms = traverserState.typeCheckedTerms.put(resultTerm, inst.type)
            ), inst
        )
    }

    protected open suspend fun traversePhiInst(inst: PhiInst) {
        val traverserState = currentState ?: return
        val previousBlock = traverserState.blockPath.last { it.method == inst.parent.method }
        val value = traverserState.mkTerm(inst.incomings.getValue(previousBlock))
        currentState = traverserState.copy(
            valueMap = traverserState.valueMap.put(inst, value)
        )
    }

    protected open suspend fun traverseUnaryInst(inst: UnaryInst) {
        var traverserState = currentState ?: return
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)

        if (inst.opcode == UnaryOpcode.LENGTH) {
            traverserState = nullabilityCheck(traverserState, inst, operandTerm) ?: return nullableCurrentState()
        }
        val clause = StateClause(
            inst,
            state { resultTerm equality operandTerm.apply(inst.opcode) }
        )
        currentState = checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            ), inst
        )
    }

    protected open suspend fun traverseJumpInst(inst: JumpInst) {
        val traverserState = currentState ?: return
        pathSelector += traverserState.copy(
            blockPath = traverserState.blockPath.add(inst.parent)
        ) to inst.successor

        currentState = null
    }

    protected open suspend fun traverseReturnInst(inst: ReturnInst) {
        val traverserState = currentState ?: return
        val stackTrace = traverserState.stackTrace
        val stackTraceElement = stackTrace.lastOrNull()
        val receiver = stackTraceElement?.instruction
        currentState = when {
            receiver == null -> {
                val result = check(rootMethod, traverserState.symbolicState)
                if (result != null) {
                    report(inst, result)
                }
                null
            }

            inst.hasReturnValue && receiver.isNameDefined -> {
                val returnTerm = traverserState.mkTerm(inst.returnValue)
                traverserState.copy(
                    valueMap = stackTraceElement.valueMap.put(receiver, returnTerm),
                    stackTrace = stackTrace.removeAt(stackTrace.lastIndex)
                )
            }

            else -> traverserState.copy(
                valueMap = stackTraceElement.valueMap,
                stackTrace = stackTrace.removeAt(stackTrace.lastIndex)
            )
        }
        if (receiver != null) {
            val nextInst = receiver.parent.indexOf(receiver) + 1
            traverseBlock(receiver.parent, nextInst)
        }
    }

    protected open suspend fun traverseSwitchInst(inst: SwitchInst) {
        val traverserState = currentState ?: return
        val key = traverserState.mkTerm(inst.key)
        for ((value, branch) in inst.branches) {
            val path = PathClause(
                PathClauseType.CONDITION_CHECK,
                inst,
                path { (key eq traverserState.mkTerm(value)) equality true }
            )
            checkReachability(
                traverserState.copy(
                    symbolicState = traverserState.symbolicState.copy(
                        path = traverserState.symbolicState.path.add(path)
                    ),
                    blockPath = traverserState.blockPath.add(inst.parent)
                ), inst
            )?.let {
                pathSelector += it to branch
            }
        }
        val defaultPath = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { key `!in` inst.operands.map { traverserState.mkTerm(it) } }
        )
        checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    path = traverserState.symbolicState.path.add(defaultPath)
                ),
                blockPath = traverserState.blockPath.add(inst.parent)
            ), inst
        )?.let {
            pathSelector += it to inst.default
        }

        currentState = null
    }

    protected open suspend fun traverseTableSwitchInst(inst: TableSwitchInst) {
        val traverserState = currentState ?: return
        val key = traverserState.mkTerm(inst.index)
        val min = inst.range.first
        for ((index, branch) in inst.branches.withIndex()) {
            val path = PathClause(
                PathClauseType.CONDITION_CHECK,
                inst,
                path { (key eq const(min + index)) equality true }
            )
            checkReachability(
                traverserState.copy(
                    symbolicState = traverserState.symbolicState.copy(
                        path = traverserState.symbolicState.path.add(path)
                    ),
                    blockPath = traverserState.blockPath.add(inst.parent)
                ), inst
            )?.let {
                pathSelector += it to branch
            }
        }
        val defaultPath = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { key `!in` inst.range.map { const(it) } }
        )
        checkReachability(
            traverserState.copy(
                symbolicState = traverserState.symbolicState.copy(
                    path = traverserState.symbolicState.path.add(defaultPath)
                ),
                blockPath = traverserState.blockPath.add(inst.parent)
            ), inst
        )?.let {
            pathSelector += it to inst.default
        }
        currentState = null
    }

    protected open suspend fun traverseThrowInst(inst: ThrowInst) {
        var traverserState = currentState ?: return
        val persistentState = traverserState.symbolicState
        val throwableTerm = traverserState.mkTerm(inst.throwable)

        traverserState = nullabilityCheck(traverserState, inst, throwableTerm) ?: return nullableCurrentState()
        val throwClause = StateClause(
            inst,
            state { `throw`(throwableTerm) }
        )
        checkExceptionAndReport(
            traverserState.copy(
                symbolicState = persistentState.copy(
                    clauses = persistentState.clauses.add(throwClause)
                )
            ),
            inst,
            throwableTerm
        )
        currentState = null
    }

    protected open suspend fun traverseUnreachableInst(inst: UnreachableInst) {
        unreachable<Unit>("Unexpected visit of $inst in symbolic traverser")
    }

    protected open suspend fun traverseUnknownValueInst(inst: UnknownValueInst) {
        unreachable<Unit>("Unexpected visit of $inst in symbolic traverser")
    }

    protected open suspend fun nullabilityCheck(state: TraverserState, inst: Instruction, term: Term): TraverserState? {
        if (term in state.nullCheckedTerms) return state
        if (term is ConstClassTerm) return state
        if (term is StaticClassRefTerm) return state
        if (term.isThis) return state

        val persistentState = state.symbolicState
        val nullityClause = PathClause(
            PathClauseType.NULL_CHECK,
            inst,
            path { (term eq null) equality true }
        )
        checkExceptionAndReport(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(nullityClause)
                )
            ),
            inst,
            generate(nullptrClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(
                        nullityClause.copy(predicate = nullityClause.predicate.inverse(ctx.random))
                    )
                ),
                nullCheckedTerms = state.nullCheckedTerms.add(term)
            ), inst
        )
    }

    protected open suspend fun boundsCheck(
        state: TraverserState,
        inst: Instruction,
        index: Term,
        length: Term
    ): TraverserState? {
        if (index to length in state.boundCheckedTerms) return state

        val persistentState = state.symbolicState
        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality false }
        )
        val lengthClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index lt length) equality false }
        )
        checkExceptionAndReport(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(zeroClause)
                )
            ),
            inst,
            generate(arrayIndexOOBClass.symbolicClass)
        )
        checkExceptionAndReport(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(lengthClause)
                )
            ),
            inst,
            generate(arrayIndexOOBClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(
                        zeroClause.copy(predicate = zeroClause.predicate.inverse(ctx.random))
                    ).add(
                        lengthClause.copy(predicate = lengthClause.predicate.inverse(ctx.random))
                    )
                ),
                boundCheckedTerms = state.boundCheckedTerms.add(index to length)
            ), inst
        )
    }

    protected open suspend fun newArrayBoundsCheck(
        state: TraverserState,
        inst: Instruction,
        index: Term
    ): TraverserState? {
        if (index to index in state.boundCheckedTerms) return state

        val persistentState = state.symbolicState
        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality false }
        )
        checkExceptionAndReport(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(zeroClause)
                )
            ),
            inst,
            generate(negativeArrayClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(
                        zeroClause.copy(predicate = zeroClause.predicate.inverse(ctx.random))
                    )
                ),
                boundCheckedTerms = state.boundCheckedTerms.add(index to index)
            ), inst
        )
    }

    protected open suspend fun typeCheck(
        state: TraverserState,
        inst: Instruction,
        term: Term,
        type: KexType
    ): TraverserState? {
        if (type !is KexPointer) return state
        val previouslyCheckedType = state.typeCheckedTerms[term]
        val currentlyCheckedType = type.getKfgType(ctx.types)
        if (previouslyCheckedType != null && currentlyCheckedType.isSubtypeOf(previouslyCheckedType)) {
            return state
        }

        val persistentState = state.symbolicState
        val typeClause = PathClause(
            PathClauseType.TYPE_CHECK,
            inst,
            path { (term `is` type) equality false }
        )
        checkExceptionAndReport(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(typeClause)
                )
            ),
            inst,
            generate(classCastClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState.copy(
                    path = persistentState.path.add(
                        typeClause.copy(predicate = typeClause.predicate.inverse(ctx.random))
                    )
                ),
                typeCheckedTerms = state.typeCheckedTerms.put(term, currentlyCheckedType)
            ), inst
        )
    }

    protected open suspend fun checkReachability(
        state: TraverserState,
        inst: Instruction
    ): TraverserState? {
        if (inst !is TerminateInst) return state
        return check(rootMethod, state.symbolicState)?.let { state }
    }

    protected open suspend fun checkExceptionAndReport(
        state: TraverserState,
        inst: Instruction,
        throwable: Term
    ) {
        val throwableType = throwable.type.getKfgType(types)
        val catchFrame: Pair<BasicBlock, PersistentMap<Value, Term>>? = state.run {
            var catcher = inst.parent.handlers.firstOrNull { throwableType.isSubtypeOf(it.exception) }
            if (catcher != null) return@run catcher to this.valueMap
            for (i in stackTrace.indices.reversed()) {
                val block = stackTrace[i].instruction.parent
                catcher = block.handlers.firstOrNull { throwableType.isSubtypeOf(it.exception) }
                if (catcher != null) return@run catcher to stackTrace[i].valueMap
            }
            null
        }
        when {
            catchFrame != null -> {
                val (catchBlock, catchValueMap) = catchFrame
                val catchInst = catchBlock.instructions.first { it is CatchInst } as CatchInst
                pathSelector += state.copy(
                    valueMap = catchValueMap.put(catchInst, throwable),
                    blockPath = state.blockPath.add(inst.parent),
                    stackTrace = state.stackTrace.builder().also {
                        while (it.isNotEmpty() && it.last().method != catchBlock.method) it.removeLast()
                        if (it.isNotEmpty()) it.removeLast()
                    }.build()
                ) to catchBlock
            }

            else -> {
                val params = check(rootMethod, state.symbolicState)
                if (params != null) {
                    report(inst, params, "_throw_${throwableType.toString().replace("[/$.]".toRegex(), "_")}")
                }
            }
        }
    }

    protected open fun report(
        inst: Instruction,
        parameters: Parameters<Descriptor>,
        testPostfix: String = ""
    ) {
        val generator = UnsafeGenerator(
            ctx,
            rootMethod,
            rootMethod.klassName + testPostfix + testIndex.getAndIncrement()
        )
        generator.generate(parameters)
        val testFile = generator.emit()
        try {
            compilerHelper.compileFile(testFile)
        } catch (e: CompilationException) {
            log.error("Failed to compile test file $testFile")
        }
    }

    protected open suspend fun check(method: Method, state: SymbolicState): Parameters<Descriptor>? =
        method.checkAsync(ctx, state)
}
