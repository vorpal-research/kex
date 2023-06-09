package org.vorpal.research.kex.asm.analysis.symbolic

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.yield
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.util.analyzeOrTimeout
import org.vorpal.research.kex.asm.analysis.util.checkAsync
import org.vorpal.research.kex.asm.analysis.util.checkAsyncIncremental
import org.vorpal.research.kex.compile.CompilationException
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.state.predicate.inverse
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.StaticClassRefTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.isThis
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.StateClause
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentPathConditionOf
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.arrayIndexOOBClass
import org.vorpal.research.kfg.classCastClass
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.ValueFactory
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.CatchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.EnterMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.ExitMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.InstanceOfInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TerminateInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.UnknownValueInst
import org.vorpal.research.kfg.ir.value.instruction.UnreachableInst
import org.vorpal.research.kfg.negativeArrayClass
import org.vorpal.research.kfg.nullptrClass
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

    operator fun plus(state: PersistentSymbolicState): TraverserState = this.copy(
        symbolicState = this.symbolicState + state
    )

    operator fun plus(clause: StateClause): TraverserState = this + (this.symbolicState + clause)
    operator fun plus(clause: PathClause): TraverserState = this + (this.symbolicState + clause)

    operator fun plus(basicBlock: BasicBlock): TraverserState = this.copy(
        blockPath = this.blockPath.add(basicBlock)
    )
}

data class SymbolicQuery(
    val query: PersistentSymbolicState,
    val action: suspend (Parameters<Descriptor>) -> Unit,
    val shouldCheck: () -> Boolean = { true }
)

@Suppress("MemberVisibilityCanBePrivate")
abstract class SymbolicTraverser(
    val ctx: ExecutionContext,
    val rootMethod: Method,
) : TermBuilder {
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

    protected val nullptrClass = cm.nullptrClass
    protected val arrayIndexOOBClass = cm.arrayIndexOOBClass
    protected val negativeArrayClass = cm.negativeArrayClass
    protected val classCastClass = cm.classCastClass

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

    protected fun nullableCurrentState() {
        currentState = null
    }

    protected inline fun acquireState(body: (TraverserState) -> Unit) {
        val traverserState = currentState ?: return
        currentState = null
        body(traverserState)
    }

    protected open suspend fun traverseArrayLoadInst(inst: ArrayLoadInst) = acquireState { traverserState ->
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val res = generate(inst.type.symbolicType)

        val (nullConditions, nullChecks) = nullabilityCheckInc(traverserState, inst, arrayTerm)
        val (boundConditions, boundChecks) = boundsCheckInc(traverserState, inst, indexTerm, arrayTerm.length())

        val clause = StateClause(inst, state { res equality arrayTerm[indexTerm].load() })
        val allChecks = buildList {
            this += SymbolicQuery(
                nullConditions + boundConditions,
                {
                    currentState = traverserState.copy(
                        symbolicState = traverserState.symbolicState + nullConditions + boundConditions + clause,
                        valueMap = traverserState.valueMap.put(inst, res)
                    )
                }
            )
            addAll(nullChecks)
            addAll(boundChecks.map { it.copy(query = nullConditions + it.query) })
        }
        checkReachabilityIncremental(traverserState.symbolicState, allChecks)
    }

    protected open suspend fun traverseArrayStoreInst(inst: ArrayStoreInst) = acquireState { traverserState ->
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val valueTerm = traverserState.mkTerm(inst.value)

        val (nullConditions, nullChecks) = nullabilityCheckInc(traverserState, inst, arrayTerm)
        val (boundConditions, boundChecks) = boundsCheckInc(traverserState, inst, indexTerm, arrayTerm.length())

        val clause = StateClause(inst, state { arrayTerm[indexTerm].store(valueTerm) })
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                this += SymbolicQuery(
                    nullConditions + boundConditions,
                    { currentState = traverserState + nullConditions + boundConditions + clause }
                )
                addAll(nullChecks)
                addAll(boundChecks.map { it.copy(query = nullConditions + it.query) })
            }
        )
    }

    protected open suspend fun traverseBinaryInst(inst: BinaryInst) = acquireState { traverserState ->
        val lhvTerm = traverserState.mkTerm(inst.lhv)
        val rhvTerm = traverserState.mkTerm(inst.rhv)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality lhvTerm.apply(resultTerm.type, inst.opcode, rhvTerm) }
        )
        currentState = traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            valueMap = traverserState.valueMap.put(inst, resultTerm)
        )
    }

    protected open suspend fun traverseBranchInst(inst: BranchInst) = acquireState { traverserState ->
        val condTerm = traverserState.mkTerm(inst.cond)

        val trueClause = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { condTerm equality true }
        )
        val falseClause = trueClause.inverse()

        val trueState = persistentSymbolicState() + trueClause
        val falseState = persistentSymbolicState() + falseClause

        checkReachabilityIncremental(
            traverserState.symbolicState,
            listOf(
                SymbolicQuery(trueState, { pathSelector += (traverserState + trueState) to inst.trueSuccessor }),
                SymbolicQuery(falseState, { pathSelector += (traverserState + falseState) to inst.falseSuccessor })
            )
        )
    }

    protected open suspend fun traverseCallInst(inst: CallInst) = acquireState { traverserState ->
        val callee = when {
            inst.isStatic -> staticRef(inst.method.klass)
            else -> traverserState.mkTerm(inst.callee)
        }
        val argumentTerms = inst.args.map { traverserState.mkTerm(it) }
        val candidates = callResolver.resolve(traverserState, inst)

        val (nullConditions, nullChecks) = when {
            inst.isStatic -> persistentSymbolicState() to emptyList()
            else -> nullabilityCheckInc(traverserState, inst, callee)
        }

        val handler: (suspend (Parameters<Descriptor>) -> Unit) = {
            when {
                candidates.isEmpty() -> {
                    var state = traverserState + nullConditions
                    val receiver = when {
                        inst.isNameDefined -> {
                            val res = generate(inst.type.symbolicType)
                            state = state.copy(
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
                    currentState = state + callClause
                }

                else -> {
                    for (candidate in candidates) {
                        processMethodCall(traverserState + nullConditions, inst, candidate, callee, argumentTerms)
                    }
                    currentState = null
                }
            }
        }

        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                add(SymbolicQuery(nullConditions, handler))
                addAll(nullChecks)
            }
        )
    }

    protected open suspend fun traverseCastInst(inst: CastInst) = acquireState { traverserState ->
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm equality (operandTerm `as` resultTerm.type) }
        )

        val (typeConditions, typeChecks) = typeCheckInc(traverserState, inst, operandTerm, resultTerm.type)
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                this += SymbolicQuery(
                    typeConditions,
                    {
                        currentState = traverserState.copy(
                            symbolicState = traverserState.symbolicState + typeConditions + clause,
                            valueMap = traverserState.valueMap.put(inst, resultTerm)
                        )
                    }
                )
                addAll(typeChecks)
            }
        )
    }

    protected open suspend fun traverseCatchInst(inst: CatchInst) {
    }

    protected open suspend fun traverseCmpInst(inst: CmpInst) = acquireState { traverserState ->
        val lhvTerm = traverserState.mkTerm(inst.lhv)
        val rhvTerm = traverserState.mkTerm(inst.rhv)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality lhvTerm.apply(inst.opcode, rhvTerm) }
        )
        currentState = traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            valueMap = traverserState.valueMap.put(inst, resultTerm)
        )
    }

    protected open suspend fun traverseEnterMonitorInst(inst: EnterMonitorInst) = acquireState { traverserState ->
        val monitorTerm = traverserState.mkTerm(inst.owner)
        val clause = StateClause(
            inst,
            state { enterMonitor(monitorTerm) }
        )

        val (typeConditions, typeChecks) = nullabilityCheckInc(traverserState, inst, monitorTerm)
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                add(SymbolicQuery(typeConditions, { currentState = traverserState + clause }))
                addAll(typeChecks)
            }
        )
    }

    protected open suspend fun traverseExitMonitorInst(inst: ExitMonitorInst) = acquireState { traverserState ->
        val monitorTerm = traverserState.mkTerm(inst.owner)
        val clause = StateClause(
            inst,
            state { exitMonitor(monitorTerm) }
        )
        currentState = traverserState + clause
    }

    protected open suspend fun traverseFieldLoadInst(inst: FieldLoadInst) = acquireState { traverserState ->
        val field = inst.field
        val objectTerm = when {
            inst.isStatic -> staticRef(field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }
        val res = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { res equality objectTerm.field(field.type.symbolicType, field.name).load() }
        )

        val (nullConstraints, nullChecks) = nullabilityCheckInc(traverserState, inst, objectTerm)
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                this += SymbolicQuery(
                    nullConstraints,
                    {
                        val newNullChecked = when {
                            field.isStatic && field.isFinal -> when (field.defaultValue) {
                                null -> traverserState.nullCheckedTerms.add(res)
                                ctx.values.nullConstant -> traverserState.nullCheckedTerms
                                else -> traverserState.nullCheckedTerms.add(res)
                            }

                            else -> traverserState.nullCheckedTerms
                        }
                        currentState = traverserState.copy(
                            symbolicState = traverserState.symbolicState + nullConstraints + clause,
                            valueMap = traverserState.valueMap.put(inst, res),
                            nullCheckedTerms = newNullChecked
                        )
                    }
                )
                addAll(nullChecks)
            }
        )
    }

    protected open suspend fun traverseFieldStoreInst(inst: FieldStoreInst) = acquireState { traverserState ->
        val objectTerm = when {
            inst.isStatic -> staticRef(inst.field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }
        val valueTerm = traverserState.mkTerm(inst.value)
        val clause = StateClause(
            inst,
            state { objectTerm.field(inst.field.type.symbolicType, inst.field.name).store(valueTerm) }
        )

        val (nullConstraints, nullChecks) = nullabilityCheckInc(traverserState, inst, objectTerm)
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                this += SymbolicQuery(
                    nullConstraints,
                    {
                        currentState = traverserState.copy(
                            symbolicState = traverserState.symbolicState + clause,
                            valueMap = traverserState.valueMap.put(inst, valueTerm)
                        )
                    }
                )
                addAll(nullChecks)
            }
        )
    }

    protected open suspend fun traverseInstanceOfInst(inst: InstanceOfInst) = acquireState { traverserState ->
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality (operandTerm `is` inst.targetType.symbolicType) }
        )
        currentState = traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            valueMap = traverserState.valueMap.put(inst, resultTerm),
            typeCheckedTerms = traverserState.typeCheckedTerms.put(operandTerm, inst.targetType)
        )
    }

    protected open suspend fun traverseInvokeDynamicInst(inst: InvokeDynamicInst) = acquireState { traverserState ->
        currentState = when (invokeDynamicResolver.resolve(traverserState, inst)) {
            null -> traverserState.copy(
                valueMap = traverserState.valueMap.put(inst, generate(inst.type.kexType))
            )

            else -> invokeDynamicResolver.resolve(traverserState, inst)
        }
    }

    protected open suspend fun processMethodCall(
        state: TraverserState,
        inst: Instruction,
        candidate: Method,
        callee: Term,
        argumentTerms: List<Term>
    ) {
        if (candidate.body.isEmpty()) return
        var newValueMap = state.valueMap.builder().let { builder ->
            if (!candidate.isStatic) builder[values.getThis(candidate.klass)] = callee
            for ((index, type) in candidate.argTypes.withIndex()) {
                builder[values.getArgument(index, candidate, type)] = argumentTerms[index]
            }
            builder.build()
        }
        var (normalConditions, checks) = when {
            candidate.isStatic -> persistentSymbolicState() to emptyList()
            else -> typeCheckInc(state, inst, callee, candidate.klass.symbolicClass)
        }
        if (candidate.klass.asType != callee.type.getKfgType(types)) {
            val newCalleeTerm = generate(candidate.klass.symbolicClass)
            val convertClause = StateClause(inst, state {
                newCalleeTerm equality (callee `as` candidate.klass.symbolicClass)
            })
            normalConditions += convertClause
            newValueMap = newValueMap.mapValues { (_, term) ->
                when (term) {
                    callee -> newCalleeTerm
                    else -> term
                }
            }.toPersistentMap()
        }

        checkReachabilityIncremental(
            state.symbolicState,
            buildList {
                this += SymbolicQuery(
                    normalConditions,
                    {
                        pathSelector.add(
                            state.copy(
                                symbolicState = state.symbolicState + normalConditions,
                                valueMap = newValueMap,
                                stackTrace = state.stackTrace.add(
                                    SymbolicStackTraceElement(inst.parent.method, inst, state.valueMap)
                                )
                            ),
                            candidate.body.entry
                        )
                    }
                )
                addAll(checks)
            }
        )
    }

    protected open suspend fun traverseNewArrayInst(inst: NewArrayInst) = acquireState { traverserState ->
        val dimensions = inst.dimensions.map { traverserState.mkTerm(it) }
        val resultTerm = generate(inst.type.symbolicType)

        var (normalConditions, checks) = persistentSymbolicState() to emptyList<SymbolicQuery>()
        for (dimension in dimensions) {
            val (dimensionCondition, dimensionChecks) = newArrayBoundsCheckInc(traverserState, inst, dimension)
            normalConditions += dimensionCondition
            checks += dimensionChecks
        }
        val clause = StateClause(
            inst,
            state { resultTerm.new(dimensions) }
        )

        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                this += SymbolicQuery(
                    normalConditions,
                    {
                        currentState = traverserState.copy(
                            symbolicState = traverserState.symbolicState + normalConditions + clause,
                            typeInfo = traverserState.typeInfo.put(resultTerm, inst.type.rtMapped),
                            valueMap = traverserState.valueMap.put(inst, resultTerm),
                            nullCheckedTerms = traverserState.nullCheckedTerms.add(resultTerm),
                            typeCheckedTerms = traverserState.typeCheckedTerms.put(resultTerm, inst.type)
                        )
                    }
                )
                addAll(checks)
            }
        )
    }

    protected open suspend fun traverseNewInst(inst: NewInst) = acquireState { traverserState ->
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm.new() }
        )
        currentState = traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            typeInfo = traverserState.typeInfo.put(resultTerm, inst.type.rtMapped),
            valueMap = traverserState.valueMap.put(inst, resultTerm),
            nullCheckedTerms = traverserState.nullCheckedTerms.add(resultTerm),
            typeCheckedTerms = traverserState.typeCheckedTerms.put(resultTerm, inst.type)
        )
    }

    protected open suspend fun traversePhiInst(inst: PhiInst) = acquireState { traverserState ->
        val previousBlock = traverserState.blockPath.last { it.method == inst.parent.method }
        val value = traverserState.mkTerm(inst.incomings.getValue(previousBlock))
        currentState = traverserState.copy(
            valueMap = traverserState.valueMap.put(inst, value)
        )
    }

    protected open suspend fun traverseUnaryInst(inst: UnaryInst) = acquireState { traverserState ->
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm equality operandTerm.apply(inst.opcode) }
        )

        if (inst.opcode == UnaryOpcode.LENGTH) {
            val (nullConditions, nullChecks) = nullabilityCheckInc(traverserState, inst, operandTerm)
            checkReachabilityIncremental(
                traverserState.symbolicState,
                buildList {
                    this += SymbolicQuery(
                        nullConditions,
                        {
                            currentState = traverserState.copy(
                                symbolicState = traverserState.symbolicState + nullConditions + clause,
                                valueMap = traverserState.valueMap.put(inst, resultTerm)
                            )
                        }
                    )
                    addAll(nullChecks)
                }
            )
        } else {
            currentState = traverserState.copy(
                symbolicState = traverserState.symbolicState + clause,
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    protected open suspend fun traverseJumpInst(inst: JumpInst) = acquireState { traverserState ->
        checkReachabilityIncremental(
            traverserState.symbolicState,
            listOf(
                SymbolicQuery(
                    persistentSymbolicState(),
                    { pathSelector += (traverserState + inst.parent) to inst.successor }
                )
            )
        )
    }

    protected open suspend fun traverseReturnInst(inst: ReturnInst) = acquireState { traverserState ->
        val stackTrace = traverserState.stackTrace
        val stackTraceElement = stackTrace.lastOrNull()
        val receiver = stackTraceElement?.instruction
        currentState = when {
            receiver == null -> {
                val result = checkIncremental(
                    rootMethod,
                    traverserState.symbolicState,
                    listOf(persistentSymbolicState())
                ).single()
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

    protected open suspend fun traverseSwitchInst(inst: SwitchInst) = acquireState { traverserState ->
        val key = traverserState.mkTerm(inst.key)
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                for ((value, branch) in inst.branches) {
                    val path = PathClause(
                        PathClauseType.CONDITION_CHECK,
                        inst,
                        path { (key eq traverserState.mkTerm(value)) equality true }
                    )
                    val pathState = persistentSymbolicState() + path
                    add(SymbolicQuery(
                        pathState,
                        { pathSelector += (traverserState + pathState) to branch }
                    ))
                }
                val defaultPath = PathClause(
                    PathClauseType.CONDITION_CHECK,
                    inst,
                    path { key `!in` inst.operands.map { traverserState.mkTerm(it) } }
                )
                val defaultState = persistentSymbolicState() + defaultPath
                add(SymbolicQuery(
                    defaultState,
                    { pathSelector += (traverserState + defaultState) to inst.default }
                ))
            }
        )
    }

    protected open suspend fun traverseTableSwitchInst(inst: TableSwitchInst) = acquireState { traverserState ->
        val key = traverserState.mkTerm(inst.index)
        val min = inst.range.first
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                for ((index, branch) in inst.branches.withIndex()) {
                    val path = PathClause(
                        PathClauseType.CONDITION_CHECK,
                        inst,
                        path { (key eq const(min + index)) equality true }
                    )
                    val pathState = persistentSymbolicState() + path
                    add(SymbolicQuery(
                        pathState,
                        { pathSelector += (traverserState + pathState) to branch }
                    ))
                }
                val defaultPath = PathClause(
                    PathClauseType.CONDITION_CHECK,
                    inst,
                    path { key `!in` inst.range.map { const(it) } }
                )
                val defaultState = persistentSymbolicState() + defaultPath
                add(SymbolicQuery(
                    defaultState,
                    { pathSelector += (traverserState + defaultState) to inst.default }
                ))
            }
        )
    }

    protected open suspend fun traverseThrowInst(inst: ThrowInst) = acquireState { traverserState ->
        val persistentState = traverserState.symbolicState
        val throwableTerm = traverserState.mkTerm(inst.throwable)
        val throwClause = StateClause(
            inst,
            state { `throw`(throwableTerm) }
        )

        val (nullConditions, nullChecks) = nullabilityCheckInc(traverserState, inst, throwableTerm)
        checkReachabilityIncremental(
            traverserState.symbolicState,
            buildList {
                add(SymbolicQuery(
                    nullConditions,
                    { parameters ->
                        throwExceptionAndReport(
                            traverserState + (persistentState + throwClause),
                            parameters,
                            inst,
                            throwableTerm
                        )
                    }
                ))
                addAll(nullChecks)
            }
        )
    }

    protected open suspend fun traverseUnreachableInst(inst: UnreachableInst) {
        unreachable<Unit>("Unexpected visit of $inst in symbolic traverser")
    }

    protected open suspend fun traverseUnknownValueInst(inst: UnknownValueInst) {
        unreachable<Unit>("Unexpected visit of $inst in symbolic traverser")
    }

    protected open suspend fun nullabilityCheckInc(
        state: TraverserState,
        inst: Instruction,
        term: Term
    ): Pair<PersistentSymbolicState, List<SymbolicQuery>> {
        if (term in state.nullCheckedTerms) return persistentSymbolicState() to emptyList()
        if (term is ConstClassTerm) return persistentSymbolicState() to emptyList()
        if (term is StaticClassRefTerm) return persistentSymbolicState() to emptyList()
        if (term.isThis) return persistentSymbolicState() to emptyList()

        val nullityClause = PathClause(
            PathClauseType.NULL_CHECK,
            inst,
            path { (term eq null) equality true }
        )

        val normalState = persistentSymbolicState() + nullityClause.inverse()
        val exceptionState = persistentSymbolicState() + nullityClause

        return normalState to listOf(
            SymbolicQuery(
                exceptionState,
                { parameters ->
                    throwExceptionAndReport(
                        state + exceptionState,
                        parameters,
                        inst,
                        generate(nullptrClass.symbolicClass)
                    )
                }
            )
        )
    }

    protected open suspend fun boundsCheckInc(
        state: TraverserState,
        inst: Instruction,
        index: Term,
        length: Term
    ): Pair<PersistentSymbolicState, List<SymbolicQuery>> {
        if (index to length in state.boundCheckedTerms) return persistentSymbolicState() to emptyList()

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

        val normalState = persistentSymbolicState() + zeroClause.inverse() + lengthClause.inverse()
        val zeroCheckState = persistentSymbolicState() + zeroClause
        val lengthCheckState = persistentSymbolicState() + lengthClause

        return normalState to listOf(
            SymbolicQuery(
                zeroCheckState,
                { parameters ->
                    throwExceptionAndReport(
                        state + zeroClause,
                        parameters,
                        inst,
                        generate(arrayIndexOOBClass.symbolicClass)
                    )
                }
            ),
            SymbolicQuery(
                lengthCheckState,
                { parameters ->
                    throwExceptionAndReport(
                        state + lengthCheckState,
                        parameters,
                        inst,
                        generate(arrayIndexOOBClass.symbolicClass)
                    )
                }
            )
        )
    }

    protected open suspend fun newArrayBoundsCheckInc(
        state: TraverserState,
        inst: Instruction,
        index: Term
    ): Pair<PersistentSymbolicState, List<SymbolicQuery>> {
        if (index to index in state.boundCheckedTerms) return persistentSymbolicState() to emptyList()

        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality false }
        )
        val normalState = persistentSymbolicState() + zeroClause.inverse()
        val zeroState = persistentSymbolicState() + zeroClause
        return normalState to listOf(
            SymbolicQuery(
                zeroState,
                { parameters ->
                    throwExceptionAndReport(
                        state + zeroState,
                        parameters,
                        inst,
                        generate(negativeArrayClass.symbolicClass)
                    )
                }
            )
        )
    }

    protected open suspend fun typeCheckInc(
        state: TraverserState,
        inst: Instruction,
        term: Term,
        type: KexType
    ): Pair<PersistentSymbolicState, List<SymbolicQuery>> {
        if (type !is KexPointer) return persistentSymbolicState() to emptyList()
        val previouslyCheckedType = state.typeCheckedTerms[term]
        val currentlyCheckedType = type.getKfgType(ctx.types)
        if (previouslyCheckedType != null && currentlyCheckedType.isSubtypeOf(previouslyCheckedType)) {
            return persistentSymbolicState() to emptyList()
        }

        val typeClause = PathClause(
            PathClauseType.TYPE_CHECK,
            inst,
            path { (term `is` type) equality false }
        )
        val normalState = persistentSymbolicState() + typeClause.inverse()
        val typeState = persistentSymbolicState() + typeClause

        return normalState to listOf(
            SymbolicQuery(
                typeState,
                { parameters ->
                    throwExceptionAndReport(
                        state + typeState,
                        parameters,
                        inst,
                        generate(classCastClass.symbolicClass)
                    )
                }
            )
        )
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use incremental API")
    protected open suspend fun nullabilityCheck(
        state: TraverserState,
        inst: Instruction,
        term: Term
    ): TraverserState? {
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
            state + nullityClause,
            inst,
            generate(nullptrClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState + nullityClause.inverse(),
                nullCheckedTerms = state.nullCheckedTerms.add(term)
            ), inst
        )
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use incremental API")
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
            state + zeroClause,
            inst,
            generate(arrayIndexOOBClass.symbolicClass)
        )
        checkExceptionAndReport(
            state + lengthClause,
            inst,
            generate(arrayIndexOOBClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState + zeroClause.inverse() + lengthClause.inverse(),
                boundCheckedTerms = state.boundCheckedTerms.add(index to length)
            ), inst
        )
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use incremental API")
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
            state + zeroClause,
            inst,
            generate(negativeArrayClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState + zeroClause.inverse(),
                boundCheckedTerms = state.boundCheckedTerms.add(index to index)
            ), inst
        )
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use incremental API")
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
            state + typeClause,
            inst,
            generate(classCastClass.symbolicClass)
        )
        return checkReachability(
            state.copy(
                symbolicState = persistentState + typeClause.inverse(),
                typeCheckedTerms = state.typeCheckedTerms.put(term, currentlyCheckedType)
            ), inst
        )
    }

    protected open suspend fun checkReachabilityIncremental(
        state: SymbolicState,
        checks: List<SymbolicQuery>
    ) {
        val results = checkIncremental(rootMethod, state, checks.map { it.query })
        for ((index, parameters) in results.withIndex()) {
            parameters ?: continue
            checks[index].action(parameters)
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use incremental API")
    protected open suspend fun checkReachability(
        state: TraverserState,
        inst: Instruction
    ): TraverserState? {
        if (inst !is TerminateInst) return state
        return check(rootMethod, state.symbolicState)?.let { state }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use incremental API")
    protected open suspend fun checkExceptionAndReport(
        state: TraverserState,
        inst: Instruction,
        throwable: Term
    ) {
        val params = check(rootMethod, state.symbolicState) ?: return
        throwExceptionAndReport(state, params, inst, throwable)
    }

    protected open suspend fun throwExceptionAndReport(
        state: TraverserState,
        parameters: Parameters<Descriptor>,
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

            else -> report(inst, parameters, "_throw_${throwableType.toString().replace("[/$.]".toRegex(), "_")}")
        }
    }

    protected open fun report(
        inst: Instruction,
        parameters: Parameters<Descriptor>,
        testPostfix: String = ""
    ): Boolean {
        val generator = UnsafeGenerator(
            ctx,
            rootMethod,
            rootMethod.klassName + testPostfix + testIndex.getAndIncrement()
        )
        generator.generate(parameters)
        val testFile = generator.emit()
        return try {
            compilerHelper.compileFile(testFile)
            true
        } catch (e: CompilationException) {
            log.error("Failed to compile test file $testFile")
            false
        }
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use incremental API")
    protected open suspend fun check(method: Method, state: SymbolicState): Parameters<Descriptor>? =
        method.checkAsync(ctx, state)

    protected open suspend fun checkIncremental(
        method: Method,
        state: SymbolicState,
        queries: List<SymbolicState>
    ): List<Parameters<Descriptor>?> =
        method.checkAsyncIncremental(ctx, state, queries)

    @Suppress("NOTHING_TO_INLINE")
    protected inline fun PathClause.inverse(): PathClause = this.copy(
        predicate = this.predicate.inverse(ctx.random)
    )
}
