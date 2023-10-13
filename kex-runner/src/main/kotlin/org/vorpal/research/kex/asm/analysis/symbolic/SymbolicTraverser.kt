package org.vorpal.research.kex.asm.analysis.symbolic

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
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
import org.vorpal.research.kex.state.term.NullTerm
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
import org.vorpal.research.kex.util.isSubtypeOfCached
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

    fun copyTermInfo(from: Term, to: Term): TraverserState = this.copy(
        nullCheckedTerms = when (from) {
            in nullCheckedTerms -> nullCheckedTerms.add(to)
            else -> nullCheckedTerms
        },
        typeCheckedTerms = when (from) {
            in typeCheckedTerms -> typeCheckedTerms.put(to, typeCheckedTerms[from]!!)
            else -> typeCheckedTerms
        }
    )

    operator fun plus(state: PersistentSymbolicState): TraverserState = this.copy(
        symbolicState = this.symbolicState + state
    )

    operator fun plus(clause: StateClause): TraverserState = this.copy(
        symbolicState = this.symbolicState + clause
    )

    operator fun plus(clause: PathClause): TraverserState = this.copy(
        symbolicState = this.symbolicState + clause
    )

    operator fun plus(basicBlock: BasicBlock): TraverserState = this.copy(
        blockPath = this.blockPath.add(basicBlock)
    )
}

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

        withContext(currentCoroutineContext()) {
            pathSelector += initialState to method.body.entry

            while (pathSelector.hasNext()) {
                val (currentState, currentBlock) = pathSelector.next()
                traverseBlock(currentState, currentBlock)
                yield()
            }
        }
    }

    protected open suspend fun traverseBlock(state: TraverserState, bb: BasicBlock, startIndex: Int = 0) {
        var currentState: TraverserState? = state
        for (index in startIndex..bb.instructions.lastIndex) {
            if (currentState == null) return
            val inst = bb.instructions[index]
            currentState = traverseInstruction(currentState, inst)
        }
    }


    protected open suspend fun traverseInstruction(state: TraverserState, inst: Instruction): TraverserState? =
        when (inst) {
            is ArrayLoadInst -> traverseArrayLoadInst(state, inst)
            is ArrayStoreInst -> traverseArrayStoreInst(state, inst)
            is BinaryInst -> traverseBinaryInst(state, inst)
            is CallInst -> traverseCallInst(state, inst)
            is CastInst -> traverseCastInst(state, inst)
            is CatchInst -> traverseCatchInst(state, inst)
            is CmpInst -> traverseCmpInst(state, inst)
            is EnterMonitorInst -> traverseEnterMonitorInst(state, inst)
            is ExitMonitorInst -> traverseExitMonitorInst(state, inst)
            is FieldLoadInst -> traverseFieldLoadInst(state, inst)
            is FieldStoreInst -> traverseFieldStoreInst(state, inst)
            is InstanceOfInst -> traverseInstanceOfInst(state, inst)
            is InvokeDynamicInst -> traverseInvokeDynamicInst(state, inst)
            is NewArrayInst -> traverseNewArrayInst(state, inst)
            is NewInst -> traverseNewInst(state, inst)
            is PhiInst -> traversePhiInst(state, inst)
            is UnaryInst -> traverseUnaryInst(state, inst)
            is BranchInst -> traverseBranchInst(state, inst)
            is JumpInst -> traverseJumpInst(state, inst)
            is ReturnInst -> traverseReturnInst(state, inst)
            is SwitchInst -> traverseSwitchInst(state, inst)
            is TableSwitchInst -> traverseTableSwitchInst(state, inst)
            is ThrowInst -> traverseThrowInst(state, inst)
            is UnreachableInst -> traverseUnreachableInst(state, inst)
            is UnknownValueInst -> traverseUnknownValueInst(state, inst)
            else -> unreachable("Unknown instruction ${inst.print()}")
        }

    protected open suspend fun traverseArrayLoadInst(
        traverserState: TraverserState,
        inst: ArrayLoadInst
    ): TraverserState? {
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val res = generate(inst.type.symbolicType)

        if (arrayTerm is NullTerm) {
            checkReachabilityIncremental(traverserState, nullabilityCheckInc(traverserState, inst, arrayTerm))
            return null
        }

        val clause = StateClause(inst, state { res equality arrayTerm[indexTerm].load() })

        val nullQueries = nullabilityCheckInc(traverserState, inst, arrayTerm)
        val boundQueries = boundsCheckInc(traverserState, inst, indexTerm, arrayTerm.length())

        var result: TraverserState? = null
        val fullQueries = (nullQueries + boundQueries.addExtraCondition(nullQueries.normalQuery))
            .withHandler { state: TraverserState ->
                state.copy(
                    symbolicState = state.symbolicState + clause,
                    valueMap = state.valueMap.put(inst, res)
                ).also { result = it }
            }

        checkReachabilityIncremental(traverserState, fullQueries)
        return result
    }

    protected open suspend fun traverseArrayStoreInst(
        traverserState: TraverserState,
        inst: ArrayStoreInst
    ): TraverserState? {
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val valueTerm = traverserState.mkTerm(inst.value)

        if (arrayTerm is NullTerm) {
            checkReachabilityIncremental(traverserState, nullabilityCheckInc(traverserState, inst, arrayTerm))
            return null
        }

        val clause = StateClause(inst, state { arrayTerm[indexTerm].store(valueTerm) })

        val nullQueries = nullabilityCheckInc(traverserState, inst, arrayTerm)
        val boundQueries = boundsCheckInc(traverserState, inst, indexTerm, arrayTerm.length())

        var result: TraverserState? = null
        val fullQueries = (nullQueries + boundQueries.addExtraCondition(nullQueries.normalQuery))
            .withHandler { state: TraverserState ->
                (state + clause).also { result = it }
            }
        checkReachabilityIncremental(traverserState, fullQueries)
        return result
    }

    protected open suspend fun traverseBinaryInst(traverserState: TraverserState, inst: BinaryInst): TraverserState? {
        val lhvTerm = traverserState.mkTerm(inst.lhv)
        val rhvTerm = traverserState.mkTerm(inst.rhv)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality lhvTerm.apply(resultTerm.type, inst.opcode, rhvTerm) }
        )
        return traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            valueMap = traverserState.valueMap.put(inst, resultTerm)
        )
    }

    protected open suspend fun traverseBranchInst(traverserState: TraverserState, inst: BranchInst): TraverserState? {
        val condTerm = traverserState.mkTerm(inst.cond)

        val trueClause = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { condTerm equality true }
        )
        val falseClause = trueClause.inverse()

        val trueConstraints = persistentSymbolicState() + trueClause
        val falseConstraints = persistentSymbolicState() + falseClause

        checkReachabilityIncremental(
            traverserState,
            ConditionCheckQuery(
                UpdateOnlyQuery(trueConstraints) { state ->
                    val newState = state + inst.parent
                    pathSelector += newState to inst.trueSuccessor
                    newState
                },
                UpdateOnlyQuery(falseConstraints) { state ->
                    val newState = state + inst.parent
                    pathSelector += newState to inst.falseSuccessor
                    newState
                },
            )
        )
        return null
    }

    protected open suspend fun traverseCallInst(traverserState: TraverserState, inst: CallInst): TraverserState? {
        val callee = when {
            inst.isStatic -> staticRef(inst.method.klass)
            else -> traverserState.mkTerm(inst.callee)
        }
        val argumentTerms = inst.args.map { traverserState.mkTerm(it) }
        val candidates = callResolver.resolve(traverserState, inst)
        var result: TraverserState? = null

        val handler: (UpdateAction) = { state ->
            when {
                candidates.isEmpty() -> {
                    var varState = state
                    val receiver = when {
                        inst.isNameDefined -> {
                            val res = generate(inst.type.symbolicType)
                            varState = varState.copy(
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
                    (varState + callClause).also {
                        result = it
                    }
                }

                else -> {
                    for (candidate in candidates) {
                        processMethodCall(state, inst, candidate, callee, argumentTerms)
                    }
                    state
                }
            }
        }

        val nullQuery = when {
            inst.isStatic -> EmptyQuery()
            else -> nullabilityCheckInc(traverserState, inst, callee)
        }.withHandler(handler)

        checkReachabilityIncremental(traverserState, nullQuery)
        return result
    }

    protected open suspend fun traverseCastInst(traverserState: TraverserState, inst: CastInst): TraverserState? {
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm equality (operandTerm `as` resultTerm.type) }
        )

        var result: TraverserState? = null
        val typeQuery = typeCheckInc(traverserState, inst, operandTerm, resultTerm.type).withHandler { state ->
            state.copy(
                symbolicState = state.symbolicState + clause,
                valueMap = state.valueMap.put(inst, resultTerm)
            ).copyTermInfo(operandTerm, resultTerm).also { result = it }
        }

        checkReachabilityIncremental(traverserState, typeQuery)
        return result
    }

    protected open suspend fun traverseCatchInst(traverserState: TraverserState, inst: CatchInst): TraverserState? {
        return traverserState
    }

    protected open suspend fun traverseCmpInst(traverserState: TraverserState, inst: CmpInst): TraverserState? {
        val lhvTerm = traverserState.mkTerm(inst.lhv)
        val rhvTerm = traverserState.mkTerm(inst.rhv)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality lhvTerm.apply(inst.opcode, rhvTerm) }
        )
        return traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            valueMap = traverserState.valueMap.put(inst, resultTerm)
        )
    }

    protected open suspend fun traverseEnterMonitorInst(
        traverserState: TraverserState,
        inst: EnterMonitorInst
    ): TraverserState? {
        val monitorTerm = traverserState.mkTerm(inst.owner)
        val clause = StateClause(
            inst,
            state { enterMonitor(monitorTerm) }
        )

        var result: TraverserState? = null
        val nullQuery = nullabilityCheckInc(traverserState, inst, monitorTerm).withHandler { state ->
            (state + clause).also {
                result = it
            }
        }
        checkReachabilityIncremental(traverserState, nullQuery)
        return result
    }

    protected open suspend fun traverseExitMonitorInst(
        traverserState: TraverserState,
        inst: ExitMonitorInst
    ): TraverserState? {
        val monitorTerm = traverserState.mkTerm(inst.owner)
        val clause = StateClause(
            inst,
            state { exitMonitor(monitorTerm) }
        )
        return traverserState + clause
    }

    protected open suspend fun traverseFieldLoadInst(
        traverserState: TraverserState,
        inst: FieldLoadInst
    ): TraverserState? {
        val field = inst.field
        val objectTerm = when {
            inst.isStatic -> staticRef(field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }

        if (objectTerm is NullTerm) {
            checkReachabilityIncremental(traverserState, nullabilityCheckInc(traverserState, inst, objectTerm))
            return null
        }

        val res = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { res equality objectTerm.field(field.type.symbolicType, field.name).load() }
        )

        var result: TraverserState? = null
        val nullQuery = nullabilityCheckInc(traverserState, inst, objectTerm).withHandler { state ->
            val newNullChecked = when {
                field.isStatic && field.isFinal -> when (field.defaultValue) {
                    null -> state.nullCheckedTerms.add(res)
                    ctx.values.nullConstant -> state.nullCheckedTerms
                    else -> state.nullCheckedTerms.add(res)
                }

                else -> state.nullCheckedTerms
            }
            state.copy(
                symbolicState = state.symbolicState + clause,
                valueMap = state.valueMap.put(inst, res),
                nullCheckedTerms = newNullChecked
            ).also { result = it }
        }
        checkReachabilityIncremental(traverserState, nullQuery)
        return result
    }

    protected open suspend fun traverseFieldStoreInst(
        traverserState: TraverserState,
        inst: FieldStoreInst
    ): TraverserState? {
        val objectTerm = when {
            inst.isStatic -> staticRef(inst.field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }

        if (objectTerm is NullTerm) {
            checkReachabilityIncremental(traverserState, nullabilityCheckInc(traverserState, inst, objectTerm))
            return null
        }

        val valueTerm = traverserState.mkTerm(inst.value)
        val clause = StateClause(
            inst,
            state { objectTerm.field(inst.field.type.symbolicType, inst.field.name).store(valueTerm) }
        )

        var result: TraverserState? = null
        val nullQuery = nullabilityCheckInc(traverserState, inst, objectTerm).withHandler { state ->
            state.copy(
                symbolicState = state.symbolicState + clause,
                valueMap = state.valueMap.put(inst, valueTerm)
            ).also { result = it }
        }
        checkReachabilityIncremental(traverserState, nullQuery)
        return result
    }

    protected open suspend fun traverseInstanceOfInst(
        traverserState: TraverserState,
        inst: InstanceOfInst
    ): TraverserState? {
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)

        val clause = StateClause(
            inst,
            state { resultTerm equality (operandTerm `is` inst.targetType.symbolicType) }
        )

        val previouslyCheckedType = traverserState.typeCheckedTerms[operandTerm]
        val currentlyCheckedType = operandTerm.type.getKfgType(ctx.types)

        return traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            valueMap = traverserState.valueMap.put(inst, resultTerm),
            typeCheckedTerms = when {
                previouslyCheckedType != null && currentlyCheckedType.isSubtypeOfCached(previouslyCheckedType) ->
                    traverserState.typeCheckedTerms.put(operandTerm, inst.targetType)

                else -> traverserState.typeCheckedTerms
            }
        )
    }

    protected open suspend fun traverseInvokeDynamicInst(
        traverserState: TraverserState,
        inst: InvokeDynamicInst
    ): TraverserState? {
        return when (invokeDynamicResolver.resolve(traverserState, inst)) {
            null -> traverserState.copy(
                valueMap = traverserState.valueMap.put(inst, generate(inst.type.kexType))
            )

            else -> invokeDynamicResolver.resolve(traverserState, inst)
        }
    }

    protected open suspend fun processMethodCall(
        traverserState: TraverserState,
        inst: Instruction,
        candidate: Method,
        callee: Term,
        argumentTerms: List<Term>
    ) {
        if (candidate.body.isEmpty()) return
        var newValueMap = traverserState.valueMap.builder().let { builder ->
            if (!candidate.isStatic) builder[values.getThis(candidate.klass)] = callee
            for ((index, type) in candidate.argTypes.withIndex()) {
                builder[values.getArgument(index, candidate, type)] = argumentTerms[index]
            }
            builder.build()
        }

        val checks = when {
            candidate.isStatic -> EmptyQuery { state ->
                state.copy(
                    valueMap = newValueMap,
                    stackTrace = state.stackTrace.add(
                        SymbolicStackTraceElement(inst.parent.method, inst, state.valueMap)
                    )
                ).also { pathSelector += it to candidate.body.entry }
            }

            else -> typeCheckInc(traverserState, inst, callee, candidate.klass.symbolicClass).withHandler { state ->
                when {
                    candidate.klass.asType.isSubtypeOfCached(callee.type.getKfgType(types)) -> {
                        val newCalleeTerm = generate(candidate.klass.symbolicClass)
                        val convertClause = StateClause(inst, state {
                            newCalleeTerm equality (callee `as` candidate.klass.symbolicClass)
                        })
                        newValueMap = newValueMap.mapValues { (_, term) ->
                            when (term) {
                                callee -> newCalleeTerm
                                else -> term
                            }
                        }.toPersistentMap()
                        state.copy(
                            symbolicState = state.symbolicState + convertClause
                        ).copyTermInfo(callee, newCalleeTerm)
                    }

                    else -> state
                }.copy(
                    valueMap = newValueMap,
                    stackTrace = state.stackTrace.add(
                        SymbolicStackTraceElement(inst.parent.method, inst, state.valueMap)
                    )
                ).also { pathSelector += it to candidate.body.entry }
            }
        }
        checkReachabilityIncremental(traverserState, checks)
    }

    protected open suspend fun traverseNewArrayInst(
        traverserState: TraverserState,
        inst: NewArrayInst
    ): TraverserState? {
        val dimensions = inst.dimensions.map { traverserState.mkTerm(it) }
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(inst, state { resultTerm.new(dimensions) })

        var result: TraverserState? = null
        val checks = dimensions.fold<Term, OptionalErrorCheckQuery>(EmptyQuery()) { acc, dimension ->
            acc + newArrayBoundsCheckInc(traverserState, inst, dimension)
        }.withHandler { state ->
            state.copy(
                symbolicState = state.symbolicState + clause,
                typeInfo = state.typeInfo.put(resultTerm, inst.type.rtMapped),
                valueMap = state.valueMap.put(inst, resultTerm),
                nullCheckedTerms = state.nullCheckedTerms.add(resultTerm),
                typeCheckedTerms = state.typeCheckedTerms.put(resultTerm, inst.type)
            ).also { result = it }
        }
        checkReachabilityIncremental(traverserState, checks)
        return result
    }

    protected open suspend fun traverseNewInst(traverserState: TraverserState, inst: NewInst): TraverserState? {
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm.new() }
        )
        return traverserState.copy(
            symbolicState = traverserState.symbolicState + clause,
            typeInfo = traverserState.typeInfo.put(resultTerm, inst.type.rtMapped),
            valueMap = traverserState.valueMap.put(inst, resultTerm),
            nullCheckedTerms = traverserState.nullCheckedTerms.add(resultTerm),
            typeCheckedTerms = traverserState.typeCheckedTerms.put(resultTerm, inst.type)
        )
    }

    protected open suspend fun traversePhiInst(traverserState: TraverserState, inst: PhiInst): TraverserState? {
        val previousBlock = traverserState.blockPath.last { it.method == inst.parent.method }
        val value = traverserState.mkTerm(inst.incomings.getValue(previousBlock))
        return traverserState.copy(
            valueMap = traverserState.valueMap.put(inst, value)
        )
    }

    protected open suspend fun traverseUnaryInst(traverserState: TraverserState, inst: UnaryInst): TraverserState? {
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm equality operandTerm.apply(inst.opcode) }
        )

        var result: TraverserState? = null
        val checks = when (inst.opcode) {
            UnaryOpcode.LENGTH -> nullabilityCheckInc(traverserState, inst, operandTerm)
            else -> EmptyQuery()
        }.withHandler { state ->
            state.copy(
                symbolicState = state.symbolicState + clause,
                valueMap = state.valueMap.put(inst, resultTerm)
            ).also { result = it }
        }

        checkReachabilityIncremental(traverserState, checks)
        return result
    }

    protected open suspend fun traverseJumpInst(traverserState: TraverserState, inst: JumpInst): TraverserState? {
        checkReachabilityIncremental(
            traverserState,
            ConditionCheckQuery(
                UpdateOnlyQuery(persistentSymbolicState()) { state ->
                    pathSelector += (state + inst.parent) to inst.successor
                    state
                }
            )
        )
        return null
    }

    protected open suspend fun traverseReturnInst(traverserState: TraverserState, inst: ReturnInst): TraverserState? {
        val stackTrace = traverserState.stackTrace
        val stackTraceElement = stackTrace.lastOrNull()
        val receiver = stackTraceElement?.instruction
        val result = when {
            receiver == null -> {
                checkReachabilityIncremental(
                    traverserState,
                    ConditionCheckQuery(
                        UpdateAndReportQuery(
                            persistentSymbolicState(),
                            { state -> state },
                            { _, parameters -> report(inst, parameters) }
                        )
                    )
                )
                return null
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
        val nextInst = receiver.parent.indexOf(receiver) + 1
        traverseBlock(result, receiver.parent, nextInst)
        return null
    }

    protected open suspend fun traverseSwitchInst(traverserState: TraverserState, inst: SwitchInst): TraverserState? {
        val key = traverserState.mkTerm(inst.key)
        checkReachabilityIncremental(
            traverserState,
            ConditionCheckQuery(buildList {
                for ((value, branch) in inst.branches) {
                    val path = PathClause(
                        PathClauseType.CONDITION_CHECK,
                        inst,
                        path { (key eq traverserState.mkTerm(value)) equality true }
                    )
                    val pathState = persistentSymbolicState() + path
                    add(UpdateOnlyQuery(pathState) { state ->
                        val newState = state + inst.parent
                        pathSelector += newState to branch
                        newState
                    })
                }
                val defaultPath = PathClause(
                    PathClauseType.CONDITION_CHECK,
                    inst,
                    path { key `!in` inst.operands.map { traverserState.mkTerm(it) } }
                )
                val defaultState = persistentSymbolicState() + defaultPath
                add(UpdateOnlyQuery(defaultState) { state ->
                    val newState = state + inst.parent
                    pathSelector += newState to inst.default
                    newState
                })
            })
        )
        return null
    }

    protected open suspend fun traverseTableSwitchInst(
        traverserState: TraverserState,
        inst: TableSwitchInst
    ): TraverserState? {
        val key = traverserState.mkTerm(inst.index)
        val min = inst.range.first
        checkReachabilityIncremental(
            traverserState,
            ConditionCheckQuery(buildList {
                for ((index, branch) in inst.branches.withIndex()) {
                    val path = PathClause(
                        PathClauseType.CONDITION_CHECK,
                        inst,
                        path { (key eq const(min + index)) equality true }
                    )
                    val pathState = persistentSymbolicState() + path
                    add(UpdateOnlyQuery(pathState) { state ->
                        val newState = state + inst.parent
                        pathSelector += newState to branch
                        newState
                    })
                }
                val defaultPath = PathClause(
                    PathClauseType.CONDITION_CHECK,
                    inst,
                    path { key `!in` inst.range.map { const(it) } }
                )
                val defaultState = persistentSymbolicState() + defaultPath
                add(UpdateOnlyQuery(defaultState) { state ->
                    val newState = state + inst.parent
                    pathSelector += newState to inst.default
                    newState
                })
            })
        )
        return null
    }

    protected open suspend fun traverseThrowInst(traverserState: TraverserState, inst: ThrowInst): TraverserState? {
        val throwableTerm = traverserState.mkTerm(inst.throwable)
        val throwClause = StateClause(
            inst,
            state { `throw`(throwableTerm) }
        )

        val checks = nullabilityCheckInc(traverserState, inst, throwableTerm).withHandler { state, parameters ->
            throwExceptionAndReport(
                state + throwClause,
                parameters,
                inst,
                throwableTerm
            )
        }
        checkReachabilityIncremental(traverserState, checks)
        return null
    }

    protected open suspend fun traverseUnreachableInst(
        traverserState: TraverserState,
        inst: UnreachableInst
    ): TraverserState? {
        return null
    }

    protected open suspend fun traverseUnknownValueInst(
        traverserState: TraverserState,
        inst: UnknownValueInst
    ): TraverserState? {
        return unreachable("Unexpected visit of $inst in symbolic traverser")
    }

    protected open suspend fun nullabilityCheckInc(
        checkState: TraverserState,
        inst: Instruction,
        term: Term
    ): OptionalErrorCheckQuery {
        if (term in checkState.nullCheckedTerms) return EmptyQuery()
        if (term is ConstClassTerm) return EmptyQuery()
        if (term is StaticClassRefTerm) return EmptyQuery()
        if (term.isThis) return EmptyQuery()

        val nullityClause = PathClause(
            PathClauseType.NULL_CHECK,
            inst,
            path { (term eq null) equality true }
        )

        val noExceptionConstraints = persistentSymbolicState() + nullityClause.inverse()
        val exceptionConstraints = persistentSymbolicState() + nullityClause

        return ExceptionCheckQuery(
            UpdateOnlyQuery(noExceptionConstraints) { noExceptionState ->
                noExceptionState.copy(nullCheckedTerms = noExceptionState.nullCheckedTerms.add(term))
            },
            ReportQuery(exceptionConstraints) { exceptionState, parameters ->
                throwExceptionAndReport(exceptionState, parameters, inst, generate(nullptrClass.symbolicClass))
            }
        )
    }

    protected open suspend fun boundsCheckInc(
        state: TraverserState,
        inst: Instruction,
        index: Term,
        length: Term
    ): OptionalErrorCheckQuery {
        if (index to length in state.boundCheckedTerms) return EmptyQuery()

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

        val noExceptionConstraints = persistentSymbolicState() + zeroClause.inverse() + lengthClause.inverse()
        val zeroCheckConstraints = persistentSymbolicState() + zeroClause
        val lengthCheckConstraints = persistentSymbolicState() + lengthClause

        return ExceptionCheckQuery(
            UpdateOnlyQuery(noExceptionConstraints) { noExceptionState ->
                noExceptionState.copy(boundCheckedTerms = noExceptionState.boundCheckedTerms.add(index to length))
            },
            ReportQuery(zeroCheckConstraints) { exceptionState, parameters ->
                throwExceptionAndReport(exceptionState, parameters, inst, generate(arrayIndexOOBClass.symbolicClass))
            },
            ReportQuery(lengthCheckConstraints) { exceptionState, parameters ->
                throwExceptionAndReport(exceptionState, parameters, inst, generate(arrayIndexOOBClass.symbolicClass))
            }
        )
    }

    protected open suspend fun newArrayBoundsCheckInc(
        state: TraverserState,
        inst: Instruction,
        index: Term
    ): OptionalErrorCheckQuery {
        if (index to index in state.boundCheckedTerms) return EmptyQuery()

        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality false }
        )
        val noExceptionConstraints = persistentSymbolicState() + zeroClause.inverse()
        val zeroCheckConstraints = persistentSymbolicState() + zeroClause
        return ExceptionCheckQuery(
            UpdateOnlyQuery(noExceptionConstraints) { noExceptionState ->
                noExceptionState.copy(boundCheckedTerms = noExceptionState.boundCheckedTerms.add(index to index))
            },
            ReportQuery(zeroCheckConstraints) { exceptionState, parameters ->
                throwExceptionAndReport(exceptionState, parameters, inst, generate(negativeArrayClass.symbolicClass))
            }
        )
    }

    protected open suspend fun typeCheckInc(
        state: TraverserState,
        inst: Instruction,
        term: Term,
        type: KexType
    ): OptionalErrorCheckQuery {
        if (type !is KexPointer) return EmptyQuery()
        val previouslyCheckedType = state.typeCheckedTerms[term]
        val currentlyCheckedType = type.getKfgType(ctx.types)
        if (previouslyCheckedType != null && currentlyCheckedType.isSubtypeOfCached(previouslyCheckedType)) {
            return EmptyQuery()
        }

        val typeClause = PathClause(
            PathClauseType.TYPE_CHECK,
            inst,
            path { (term `is` type) equality false }
        )
        val noExceptionConstraints = persistentSymbolicState() + typeClause.inverse()
        val typeCheckConstraints = persistentSymbolicState() + typeClause

        return ExceptionCheckQuery(
            UpdateOnlyQuery(noExceptionConstraints) { noExceptionState ->
                noExceptionState.copy(
                    typeCheckedTerms = noExceptionState.typeCheckedTerms.put(
                        term,
                        currentlyCheckedType
                    )
                )
            },
            ReportQuery(typeCheckConstraints) { exceptionState, parameters ->
                throwExceptionAndReport(exceptionState, parameters, inst, generate(classCastClass.symbolicClass))
            }
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
        if (previouslyCheckedType != null && currentlyCheckedType.isSubtypeOfCached(previouslyCheckedType)) {
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
        state: TraverserState,
        checks: IncrementalQuery
    ) {
        when (checks) {
            is EmptyQuery -> checks.action(state)
            is ExceptionCheckQuery -> {
                val results = checkIncremental(rootMethod, state.symbolicState, buildList {
                    add(checks.noErrorQuery.query)
                    addAll(checks.errorQueries.map { it.query })
                })

                if (results[0] != null) {
                    checks.noErrorQuery.action(state + checks.noErrorQuery.query)
                }
                for (index in checks.errorQueries.indices) {
                    val currentResult = results[index + 1] ?: continue
                    val currentQuery = checks.errorQueries[index]
                    val newState = state + currentQuery.query
                    when (currentQuery) {
                        is UpdateOnlyQuery -> currentQuery.action(newState)
                        is UpdateAndReportQuery -> {
                            currentQuery.action(newState)
                            currentQuery.reportAction(newState, currentResult)
                        }

                        is ReportQuery -> currentQuery.action(newState, currentResult)
                    }
                }
            }

            is ConditionCheckQuery -> {
                val results = checkIncremental(rootMethod, state.symbolicState, checks.queries.map { it.query })
                for ((result, query) in results.zip(checks.queries)) {
                    if (result != null) {
                        val newState = state + query.query
                        when (query) {
                            is UpdateOnlyQuery -> query.action(newState)
                            is UpdateAndReportQuery -> {
                                query.action(newState)
                                query.reportAction(newState, result)
                            }
                        }
                    }
                }
            }
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
            var catcher = inst.parent.handlers.firstOrNull { throwableType.isSubtypeOfCached(it.exception) }
            if (catcher != null) return@run catcher to this.valueMap
            for (i in stackTrace.indices.reversed()) {
                val block = stackTrace[i].instruction.parent
                catcher = block.handlers.firstOrNull { throwableType.isSubtypeOfCached(it.exception) }
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
