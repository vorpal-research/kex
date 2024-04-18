package org.vorpal.research.kex.asm.analysis.concolic.coverage

import kotlinx.collections.immutable.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelector
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelectorManager
import org.vorpal.research.kex.asm.analysis.symbolic.*
import org.vorpal.research.kex.asm.analysis.util.checkAsync
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.inverse
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kex.state.term.TermBuilder.Terms.arg
import org.vorpal.research.kex.state.term.TermBuilder.Terms.generate
import org.vorpal.research.kex.state.term.TermBuilder.Terms.length
import org.vorpal.research.kex.state.term.TermBuilder.Terms.staticRef
import org.vorpal.research.kex.state.term.TermBuilder.Terms.`this`
import org.vorpal.research.kex.state.transformer.isThis
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.ValueFactory
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

//data class TraverserState(
//    val symbolicState: PersistentSymbolicState,
//    val valueMap: PersistentMap<Value, Term>,
//    val stackTrace: PersistentList<SymbolicStackTraceElement>,
//    val typeInfo: PersistentMap<Term, Type>,
//    val blockPath: PersistentList<BasicBlock>,
//    val nullCheckedTerms: PersistentSet<Term>,
//    val boundCheckedTerms: PersistentSet<Pair<Term, Term>>,
//    val typeCheckedTerms: PersistentMap<Term, Type>
//) {
//    fun mkTerm(value: Value): Term = when (value) {
//        is Constant -> term { const(value) }
//        else -> valueMap.getValue(value)
//    }
//
//    fun copyTermInfo(from: Term, to: Term): TraverserState = this.copy(
//        nullCheckedTerms = when (from) {
//            in nullCheckedTerms -> nullCheckedTerms.add(to)
//            else -> nullCheckedTerms
//        },
//        typeCheckedTerms = when (from) {
//            in typeCheckedTerms -> typeCheckedTerms.put(to, typeCheckedTerms[from]!!)
//            else -> typeCheckedTerms
//        }
//    )
//
//    operator fun plus(state: PersistentSymbolicState): TraverserState = this.copy(
//        symbolicState = this.symbolicState + state
//    )
//
//    operator fun plus(clause: StateClause): TraverserState = this.copy(
//        symbolicState = this.symbolicState + clause
//    )
//
//    operator fun plus(clause: PathClause): TraverserState = this.copy(
//        symbolicState = this.symbolicState + clause
//    )
//
//    operator fun plus(basicBlock: BasicBlock): TraverserState = this.copy(
//        blockPath = this.blockPath.add(basicBlock)
//    )
//}

class ExperimentPathSelectorManager (
    override val ctx: ExecutionContext,
    override val targets: Set<Method>
) : ConcolicPathSelectorManager {

    private val targetInstructions = targets.flatMapTo(mutableSetOf()) { it.body.flatten() }
    private val coveredInstructions = mutableSetOf<Instruction>()

    val weightedGraph = WeightedGraph(targets, targetInstructions)

    fun isCovered(): Boolean {
        val result = coveredInstructions.containsAll(targetInstructions) ||
                weightedGraph.targets.sumOf { weightedGraph.getVertex(it.body.entry.instructions.first()).score } == 0
        log.debug("Temp")
        return result
    }

    fun addCoverage(trace: List<Instruction>) {
        coveredInstructions += trace
    }

    override fun createPathSelectorFor(target: Method): ConcolicPathSelector = ExperimentPathSelector(this)
}

class ExperimentPathSelector(
    private val manager: ExperimentPathSelectorManager
) : ConcolicPathSelector {

    override val ctx: ExecutionContext
        get() = manager.ctx

    override suspend fun isEmpty(): Boolean = manager.isCovered()

    override suspend fun addExecutionTrace(
        method: Method,
        checkedState: PersistentSymbolicState,
        result: ExecutionCompletedResult
    ) {
        manager.addCoverage(result.trace)
        manager.weightedGraph.addTrace(result.trace)
    }

    override fun reverse(pathClause: PathClause): PathClause? {
        TODO("Not yet implemented")
    }

    override suspend fun hasNext(): Boolean = !isEmpty()

    override suspend fun next(): Pair<Method, PersistentSymbolicState> {
        val bestMethod = manager.targets.maxBy { manager.weightedGraph.getVertex(it.body.entry.instructions.first()).score }
        val root = bestMethod.body.entry.instructions.first()
        val path = manager.weightedGraph.getPath(root)
        val state = processMethod(bestMethod, path)
        return Pair(bestMethod, state.symbolicState)
    }

    protected val Type.symbolicType: KexType get() = kexType.rtMapped
    protected val org.vorpal.research.kfg.ir.Class.symbolicClass: KexType get() = kexType.rtMapped

    val types: TypeFactory
        get() = ctx.types

    val values: ValueFactory
        get() = ctx.values

    protected open suspend fun processMethod(method: Method, path: List<WeightedGraph.Vertex>): TraverserState {
        val thisValue = values.getThis(method.klass)
        val initialArguments = buildMap {
            val values = this@ExperimentPathSelector.values
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

        return getPersistentState(method, initialState, path)
    }

    suspend fun findFirstUnreachable(method: Method, pathStates: List<TraverserState>): Int {
        if (method.checkAsync(ctx, pathStates.last().symbolicState) != null) return -1
        var startRange = 0
        var endRange = pathStates.size - 1
        while (endRange - startRange > 0) {
            val pivot = (startRange + endRange) / 2
            val res = method.checkAsync(ctx, pathStates[pivot].symbolicState)
            if (res == null) {
                endRange = pivot
            }
            else {
                startRange = pivot + 1
            }
        }
        return endRange
    }

    suspend fun getPersistentState(method: Method, state: TraverserState, path: List<WeightedGraph.Vertex>): TraverserState {
        var currentState: TraverserState = state
        val instList = path.map { it.instruction }
        log.debug(instList.toString())
        var pathStates = mutableListOf<TraverserState>()
        for (i in 0 until path.size-1) {
            val inst = path[i].instruction
            val nextInst = path.getOrNull(i+1)?.instruction
            val newState = traverseInstruction(currentState, inst, nextInst)
            if (newState == null) return currentState
            currentState = newState
            pathStates.add(currentState)
        }
        val lastPathClause = pathStates.indexOfFirst { it.symbolicState.path.size == currentState.symbolicState.path.size }

        val firstUnreachable = findFirstUnreachable(method, pathStates.subList(0, lastPathClause+1))
        if (firstUnreachable != -1) {
            // go down until vertex with multiple possible paths
            // it is needed because path clause added by this vertex is causing unreachability, the inst itself may be reachable
            manager.weightedGraph.unreachables.add(path.slice(0..firstUnreachable+1))
            manager.weightedGraph.getVertex(path[firstUnreachable].instruction).invalidate()

            return pathStates[firstUnreachable-1]
        }
//        val concreteTypes: MutableMap<Term, KexType> = mutableMapOf()
//        currentState.symbolicState.clauses.forEach { clause ->
//            clause.predicate.operands.forEach { term ->
//                if (term.type.javaName.contains("java.util")) {
//                    concreteTypes[term] =
//                        instantiationManager.getConcreteType(term.type, manager.ctx.cm, ctx.accessLevel, ctx.random)
//                }
//                term.subTerms.forEach { subTerm ->
//                    if (subTerm.type.javaName.contains("java.util")) {
//                        concreteTypes[subTerm] =
//                            instantiationManager.getConcreteType(subTerm.type, manager.ctx.cm, ctx.accessLevel, ctx.random)
//                    }
//                }
//            }
//        }
        //currentState.symbolicState.concreteTypes = concreteTypes.toPersistentMap()
        val resultState = pathStates.getOrNull(lastPathClause+1) ?: currentState
        return resultState
    }

    suspend fun traverseInstruction(state: TraverserState, inst: Instruction, nextInstruction: Instruction?): TraverserState? {
        try {
            return when (inst) {
                is ArrayLoadInst -> traverseArrayLoadInst(state, inst, nextInstruction)
                is ArrayStoreInst -> traverseArrayStoreInst(state, inst, nextInstruction)
                is BinaryInst -> traverseBinaryInst(state, inst)
                is CallInst -> traverseCallInst(state, inst, nextInstruction)
                is CastInst -> traverseCastInst(state, inst, nextInstruction)
                is CatchInst -> traverseCatchInst(state, inst)
                is CmpInst -> traverseCmpInst(state, inst)
                is EnterMonitorInst -> traverseEnterMonitorInst(state, inst, nextInstruction)
                is ExitMonitorInst -> traverseExitMonitorInst(state, inst)
                is FieldLoadInst -> traverseFieldLoadInst(state, inst, nextInstruction)
                is FieldStoreInst -> traverseFieldStoreInst(state, inst, nextInstruction)
                is InstanceOfInst -> traverseInstanceOfInst(state, inst)
                is InvokeDynamicInst -> traverseInvokeDynamicInst(state, inst)
                is NewArrayInst -> traverseNewArrayInst(state, inst, nextInstruction)
                is NewInst -> traverseNewInst(state, inst)
                is PhiInst -> traversePhiInst(state, inst)
                is UnaryInst -> traverseUnaryInst(state, inst, nextInstruction)
                is BranchInst -> traverseBranchInst(state, inst, nextInstruction)
                is JumpInst -> traverseJumpInst(state, inst)
                is ReturnInst -> traverseReturnInst(state, inst)
                is SwitchInst -> traverseSwitchInst(state, inst, nextInstruction)
                is TableSwitchInst -> traverseTableSwitchInst(state, inst, nextInstruction)
                is ThrowInst -> traverseThrowInst(state, inst, nextInstruction)
                is UnreachableInst -> traverseUnreachableInst(state, inst)
                is UnknownValueInst -> traverseUnknownValueInst(state, inst)
                else -> unreachable("Unknown instruction ${inst.print()}")
            }
        } catch (e: Exception) {
            log.debug(e.toString())
            return state
        }
    }

    fun nullCheck(
        traverserState: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        term: Term
    ): Pair<Boolean, TraverserState> {
        if (term in traverserState.nullCheckedTerms) return Pair(true, traverserState)
        if (term is ConstClassTerm) return Pair(true, traverserState)
        if (term is StaticClassRefTerm) return Pair(true, traverserState)
        if (term.isThis) return Pair(true, traverserState)

        val nullityClause = PathClause(
            PathClauseType.NULL_CHECK,
            inst,
            path { (term eq null) equality true }
        )
        return if (nextInstruction is CatchInst) {
            Pair(false, traverserState + nullityClause)
        }
        else {
            Pair(true, traverserState + nullityClause.inverse())
        }
    }

    fun boundsCheck(
        traverserState: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        index: Term,
        length: Term
    ): Pair<Boolean, TraverserState> {
        if (index to index in traverserState.boundCheckedTerms) return Pair(true, traverserState)
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
        // TODO: think about other case
        return if (nextInstruction is CatchInst) {
            Pair(false, traverserState + zeroClause)
        }
        else {
            Pair(true, traverserState + zeroClause.inverse() + lengthClause.inverse())
        }
    }

    fun typeCheck(
        state: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        term: Term,
        type: KexType
    ): Pair<Boolean, TraverserState> {
        if (type !is KexPointer) return Pair(true, state)
        val previouslyCheckedType = state.typeCheckedTerms[term]
        val currentlyCheckedType = type.getKfgType(ctx.types)
        if (previouslyCheckedType != null && currentlyCheckedType.isSubtypeOfCached(previouslyCheckedType)) {
            return Pair(true, state)
        }

        val typeClause = PathClause(
            PathClauseType.TYPE_CHECK,
            inst,
            path { (term `is` type) equality false }
        )

        return if (nextInstruction is CatchInst) {
            Pair(false, state + typeClause)
        }
        else {
            Pair(true, state + typeClause.inverse())
        }
    }

    fun newArrayBoundsCheck(
        state: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        index: Term
    ): Pair<Boolean, TraverserState> {
        if (index to index in state.boundCheckedTerms) return Pair(true, state)

        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality false }
        )
        val noExceptionConstraints = persistentSymbolicState() + zeroClause.inverse()
        val zeroCheckConstraints = persistentSymbolicState() + zeroClause

        if (nextInstruction is CatchInst) {
            return Pair(false, state + zeroCheckConstraints)
        }
        else {
            val res = state + noExceptionConstraints
            return Pair(true, res.copy(boundCheckedTerms = res.boundCheckedTerms.add(index to index)) + noExceptionConstraints)
        }
    }

    protected open suspend fun traverseArrayLoadInst(
        traverserState: TraverserState,
        inst: ArrayLoadInst,
        nextInstruction: Instruction?
    ): TraverserState? {
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val res = generate(inst.type.symbolicType)

        if (arrayTerm is NullTerm) {
            return nullCheck(traverserState, inst, nextInstruction, arrayTerm).second
        }

        val clause = StateClause(inst, state { res equality arrayTerm[indexTerm].load() })

        var result = nullCheck(traverserState, inst, nextInstruction, arrayTerm)
        if (!result.first) {
            return result.second
        }
        result = boundsCheck(result.second, inst, nextInstruction, indexTerm, arrayTerm.length())
        if (!result.first) {
            return result.second
        }
        return result.second.copy(
            symbolicState = result.second.symbolicState + clause,
            valueMap = result.second.valueMap.put(inst, res)
        )
    }

    protected open suspend fun traverseArrayStoreInst(
        traverserState: TraverserState,
        inst: ArrayStoreInst,
        nextInstruction: Instruction?
    ): TraverserState? {
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val valueTerm = traverserState.mkTerm(inst.value)

        if (arrayTerm is NullTerm) {
            return nullCheck(traverserState, inst, nextInstruction, arrayTerm).second
        }

        val clause = StateClause(inst, state { arrayTerm[indexTerm].store(valueTerm) })

        var result = nullCheck(traverserState, inst, nextInstruction, arrayTerm)
        if (!result.first) {
            return result.second
        }
        result = boundsCheck(result.second, inst, nextInstruction, indexTerm, arrayTerm.length())
        if (!result.first) {
            return result.second
        }
        return result.second + clause
    }

    protected open suspend fun traverseBinaryInst(traverserState: TraverserState, inst: BinaryInst): TraverserState {
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

    protected open suspend fun traverseBranchInst(
        traverserState: TraverserState,
        inst: BranchInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val condTerm = traverserState.mkTerm(inst.cond)

        val trueClause = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { condTerm equality true }
        )
        val falseClause = trueClause.inverse()

        if (nextInstruction in inst.trueSuccessor) {
            return traverserState + trueClause + inst.parent
        }
        else return traverserState + falseClause + inst.parent
    }

    val callResolver: SymbolicCallResolver = DefaultCallResolver(ctx)

    protected open suspend fun traverseCallInst(
        traverserState: TraverserState,
        inst: CallInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val callee = when {
            inst.isStatic -> staticRef(inst.method.klass)
            else -> traverserState.mkTerm(inst.callee)
        }
        val argumentTerms = inst.args.map { traverserState.mkTerm(it) }
        val candidates = callResolver.resolve(traverserState, inst)

        var (isCheckSuccess, result) = nullCheck(traverserState, inst, nextInstruction, callee)
        if (!isCheckSuccess) {
            return result
        }
        val candidate = candidates.find { !it.body.entry.isEmpty && it.body.entry.instructions[0] == nextInstruction }
        result = when {
            candidate == null -> {
                var varState = result
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
                varState + callClause
            }

            else -> processMethodCall(result, inst, nextInstruction, candidate, callee, argumentTerms)
        }
        return result
    }

    protected open suspend fun traverseCastInst(
        traverserState: TraverserState,
        inst: CastInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm equality (operandTerm `as` resultTerm.type) }
        )

        var (isCheckSuccess, result) = typeCheck(traverserState, inst, nextInstruction, operandTerm, resultTerm.type)
        if (!isCheckSuccess) {
            return result
        }
        result = result.copy(
            symbolicState = result.symbolicState + clause,
            valueMap = result.valueMap.put(inst, resultTerm)
        ).copyTermInfo(operandTerm, resultTerm)

        return result
    }

    protected open suspend fun traverseCatchInst(traverserState: TraverserState, inst: CatchInst): TraverserState {
        return traverserState
    }

    protected open suspend fun traverseCmpInst(
        traverserState: TraverserState,
        inst: CmpInst
    ): TraverserState {
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
        inst: EnterMonitorInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val monitorTerm = traverserState.mkTerm(inst.owner)
        val clause = StateClause(
            inst,
            state { enterMonitor(monitorTerm) }
        )

        val (isCheckSuccess, result) = nullCheck(traverserState, inst, nextInstruction, monitorTerm)
        if (!isCheckSuccess) {
            return result
        }
        return result + clause
    }

    protected open suspend fun traverseExitMonitorInst(
        traverserState: TraverserState,
        inst: ExitMonitorInst
    ): TraverserState {
        val monitorTerm = traverserState.mkTerm(inst.owner)
        val clause = StateClause(
            inst,
            state { exitMonitor(monitorTerm) }
        )
        return traverserState + clause
    }

    protected open suspend fun traverseFieldLoadInst(
        traverserState: TraverserState,
        inst: FieldLoadInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val field = inst.field
        val objectTerm = when {
            inst.isStatic -> staticRef(field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }

        if (objectTerm is NullTerm) {
            return nullCheck(traverserState, inst, nextInstruction, objectTerm).second
        }

        val res = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { res equality objectTerm.field(field.type.symbolicType, field.name).load() }
        )

        val (isCheckSuccess, result) = nullCheck(traverserState, inst, nextInstruction, objectTerm)
        if (!isCheckSuccess) return result

        val newNullChecked = when {
            field.isStatic && field.isFinal -> when (field.defaultValue) {
                null -> result.nullCheckedTerms.add(res)
                ctx.values.nullConstant -> result.nullCheckedTerms
                else -> result.nullCheckedTerms.add(res)
            }

            else -> result.nullCheckedTerms
        }
        return result.copy(
            symbolicState = result.symbolicState + clause,
            valueMap = result.valueMap.put(inst, res),
            nullCheckedTerms = newNullChecked
        )
    }

    protected open suspend fun traverseFieldStoreInst(
        traverserState: TraverserState,
        inst: FieldStoreInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val objectTerm = when {
            inst.isStatic -> staticRef(inst.field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }

        if (objectTerm is NullTerm) {
            return nullCheck(traverserState, inst, nextInstruction, objectTerm).second
        }

        val valueTerm = traverserState.mkTerm(inst.value)
        val clause = StateClause(
            inst,
            state { objectTerm.field(inst.field.type.symbolicType, inst.field.name).store(valueTerm) }
        )

        val (isCheckSuccess, result) = nullCheck(traverserState, inst, nextInstruction, objectTerm)
        if (!isCheckSuccess) return result

        return result.copy(
            symbolicState = result.symbolicState + clause,
            valueMap = result.valueMap.put(inst, valueTerm)
        )
    }

    protected open suspend fun traverseInstanceOfInst(
        traverserState: TraverserState,
        inst: InstanceOfInst
    ): TraverserState {
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

    val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)

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
        nextInstruction: Instruction?,
        candidate: Method,
        callee: Term,
        argumentTerms: List<Term>
    ): TraverserState {
        if (candidate.body.isEmpty()) return traverserState

        var newValueMap = traverserState.valueMap.builder().let { builder ->
            if (!candidate.isStatic) builder[values.getThis(candidate.klass)] = callee
            for ((index, type) in candidate.argTypes.withIndex()) {
                builder[values.getArgument(index, candidate, type)] = argumentTerms[index]
            }
            builder.build()
        }

        when {
            candidate.isStatic -> return traverserState.copy(
                valueMap = newValueMap,
                stackTrace = traverserState.stackTrace.add(
                    SymbolicStackTraceElement(inst.parent.method, inst, traverserState.valueMap)
                )
            )

            else -> {
                var (isCheckSuccess, result) = typeCheck(traverserState, inst, nextInstruction, callee, candidate.klass.symbolicClass)
                if (!isCheckSuccess) {
                    return result
                }
                result = when {
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
                        result.copy(
                            symbolicState = result.symbolicState + convertClause
                        ).copyTermInfo(callee, newCalleeTerm)
                    }

                    else -> traverserState
                }.copy(
                    valueMap = newValueMap,
                    stackTrace = result.stackTrace.add(
                        SymbolicStackTraceElement(inst.parent.method, inst, result.valueMap)
                    )
                )
                return result
            }
        }
    }

    protected open suspend fun traverseNewArrayInst(
        traverserState: TraverserState,
        inst: NewArrayInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val dimensions = inst.dimensions.map { traverserState.mkTerm(it) }
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(inst, state { resultTerm.new(dimensions) })

        var result: TraverserState = traverserState
        dimensions.forEach { dimension ->
            val r = newArrayBoundsCheck(traverserState, inst, nextInstruction, dimension)
            if (!r.first) {
                return result
            }
            result = r.second
        }

        return result.copy(
            symbolicState = result.symbolicState + clause,
            typeInfo = result.typeInfo.put(resultTerm, inst.type.rtMapped),
            valueMap = result.valueMap.put(inst, resultTerm),
            nullCheckedTerms = result.nullCheckedTerms.add(resultTerm),
            typeCheckedTerms = result.typeCheckedTerms.put(resultTerm, inst.type)
        )
    }

    protected open suspend fun traverseNewInst(
        traverserState: TraverserState,
        inst: NewInst
    ): TraverserState {
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

    protected open suspend fun traversePhiInst(
        traverserState: TraverserState,
        inst: PhiInst
    ): TraverserState {
        val previousBlock = traverserState.blockPath.last { it.method == inst.parent.method }
        val value = traverserState.mkTerm(inst.incomings.getValue(previousBlock))
        return traverserState.copy(
            valueMap = traverserState.valueMap.put(inst, value)
        )
    }

    protected open suspend fun traverseUnaryInst(
        traverserState: TraverserState,
        inst: UnaryInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val operandTerm = traverserState.mkTerm(inst.operand)
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { resultTerm equality operandTerm.apply(inst.opcode) }
        )

        val result: TraverserState = when (inst.opcode) {
            UnaryOpcode.LENGTH -> nullCheck(traverserState, inst, nextInstruction, operandTerm).second
            else -> traverserState
        }

        return result.copy(
            symbolicState = result.symbolicState + clause,
            valueMap = result.valueMap.put(inst, resultTerm)
        )
    }

    protected open suspend fun traverseJumpInst(
        traverserState: TraverserState,
        inst: JumpInst
    ): TraverserState {
        return traverserState + inst.parent
    }

    protected open suspend fun traverseReturnInst(
        traverserState: TraverserState,
        inst: ReturnInst
    ): TraverserState {
        val stackTrace = traverserState.stackTrace
        val stackTraceElement = stackTrace.lastOrNull()
        val receiver = stackTraceElement?.instruction
        val result = when {
            receiver == null -> {
                return traverserState
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
        return result
    }

    protected open suspend fun traverseSwitchInst(
        traverserState: TraverserState,
        inst: SwitchInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val key = traverserState.mkTerm(inst.key)

        for ((value, branch) in inst.branches) {
            if (nextInstruction !in branch.instructions) {
                continue
            }
            val path = PathClause(
                PathClauseType.CONDITION_CHECK,
                inst,
                path { (key eq traverserState.mkTerm(value)) equality true }
            )
            return traverserState + path + inst.parent
        }
        val defaultPath = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { key `!in` inst.operands.map { traverserState.mkTerm(it) } }
        )
        return traverserState + defaultPath + inst.parent
    }

    protected open suspend fun traverseTableSwitchInst(
        traverserState: TraverserState,
        inst: TableSwitchInst,
        nextInstruction: Instruction?
    ): TraverserState? {
        val key = traverserState.mkTerm(inst.index)
        val min = inst.range.first
        for ((index, branch) in inst.branches.withIndex()) {
            if (nextInstruction !in branch.instructions) {
                continue
            }
            val path = PathClause(
                PathClauseType.CONDITION_CHECK,
                inst,
                path { (key eq const(min + index)) equality true }
            )
            return traverserState + path + inst.parent
        }
        val defaultPath = PathClause(
            PathClauseType.CONDITION_CHECK,
            inst,
            path { key `!in` inst.range.map { const(it) } }
        )
        return traverserState + defaultPath + inst.parent
    }

    protected open suspend fun traverseThrowInst(
        traverserState: TraverserState,
        inst: ThrowInst,
        nextInstruction: Instruction?
    ): TraverserState? {
        val throwableTerm = traverserState.mkTerm(inst.throwable)
        val throwClause = StateClause(
            inst,
            state { `throw`(throwableTerm) }
        )

        var (isCheckPassed, result) = nullCheck(traverserState, inst, nextInstruction, throwableTerm)
        if (!isCheckPassed) {
            return result
        }
        return result + throwClause
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

    @Suppress("NOTHING_TO_INLINE")
    protected inline fun PathClause.inverse(): PathClause = this.copy(
        predicate = this.predicate.inverse(ctx.random)
    )
}