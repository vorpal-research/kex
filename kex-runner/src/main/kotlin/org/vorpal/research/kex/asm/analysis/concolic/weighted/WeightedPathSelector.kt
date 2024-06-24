package org.vorpal.research.kex.asm.analysis.concolic.weighted

import kotlinx.collections.immutable.*
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
import kotlin.math.pow


class WeightedPathSelectorManager (
    override val ctx: ExecutionContext,
    override val targets: Set<Method>
) : ConcolicPathSelectorManager {

    private val targetInstructions = targets.flatMapTo(mutableSetOf()) { it.body.flatten() }
    private val coveredInstructions = mutableSetOf<Instruction>()
    private var stage = 1
    private val MAX_STAGE = 3

    val weightedGraph = WeightedGraph(ctx, targets, targetInstructions)

    fun isCovered(): Boolean {
        val isStageCovered = coveredInstructions.containsAll(targetInstructions) || weightedGraph.targets.all {
            weightedGraph.getVertex(it.body.entry.instructions.first()).score < weightedGraph.ISUFFICIENT_PATH_SCORE
        }

        if (isStageCovered) {
            stage++
            if (stage <= MAX_STAGE) {
                weightedGraph.reassignCyclesEdgesScores(10.0.pow(stage.toDouble()).toInt())
            }
        }
        return stage > MAX_STAGE
    }

    fun addCoverage(trace: List<Instruction>) {
        coveredInstructions += trace.filter { it in targetInstructions }
    }

    override fun createPathSelectorFor(target: Method): ConcolicPathSelector = WeightedPathSelector(this)
}

class WeightedPathSelector(
    private val manager: WeightedPathSelectorManager
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
        if (path.size <= 1) {
            return Pair(bestMethod, persistentSymbolicState())
        }
        val state = processMethod(bestMethod, path)
        return Pair(bestMethod, state.symbolicState)
    }

    private val Type.symbolicType: KexType get() = kexType.rtMapped
    private val org.vorpal.research.kfg.ir.Class.symbolicClass: KexType get() = kexType.rtMapped

    val types: TypeFactory
        get() = ctx.types

    val values: ValueFactory
        get() = ctx.values

    private suspend fun processMethod(method: Method, path: List<WeightedGraph.Vertex>): TraverserState {
        val thisValue = values.getThis(method.klass)
        val initialArguments = buildMap {
            val values = this@WeightedPathSelector.values
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

    // binary search for first unreachable
    private suspend fun findFirstUnreachable(method: Method, pathStates: List<TraverserState>): Int {
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

    private suspend fun getPersistentState(method: Method, state: TraverserState, path: List<WeightedGraph.Vertex>): TraverserState {
        var currentState: TraverserState = state
        // pathStates contains history of traversal state
        val pathStates = mutableListOf(state)
        for (i in 0 until path.size-1) {
            val inst = path[i].instruction
            val nextInst = path.getOrNull(i+1)?.instruction
            val newState = traverseInstruction(currentState, inst, nextInst) ?: break
            currentState = newState
            pathStates.add(currentState)
        }
        // for finding first unreachable we only need everything before path clause + chosen path
        val lastPathClause = pathStates.indexOfFirst { it.symbolicState.path.size == currentState.symbolicState.path.size }
        val firstUnreachable = findFirstUnreachable(method, pathStates.subList(0, lastPathClause+1))

        // -1 means the built path is reachable
        if (firstUnreachable != -1) {
            manager.weightedGraph.changePathScoreMultiplier(path.slice(0..firstUnreachable), 0.0)
            return pathStates[firstUnreachable-1]
        }
        val resultState = pathStates.getOrNull(lastPathClause + 1) ?: currentState
        return resultState
    }

    private fun traverseInstruction(state: TraverserState, inst: Instruction, nextInstruction: Instruction?): TraverserState? {
        try {
            return when (inst) {
                is ArrayLoadInst -> traverseArrayLoadInst(state, inst, nextInstruction)
                is ArrayStoreInst -> traverseArrayStoreInst(state, inst, nextInstruction)
                is BinaryInst -> traverseBinaryInst(state, inst)
                is CallInst -> traverseCallInst(state, inst, nextInstruction)
                is CastInst -> traverseCastInst(state, inst, nextInstruction)
                is CatchInst -> traverseCatchInst(state)
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
                is UnreachableInst -> traverseUnreachableInst()
                is UnknownValueInst -> traverseUnknownValueInst(inst)
                else -> unreachable("Unknown instruction ${inst.print()}")
            }
        } catch (e: Exception) {
            log.debug(e.stackTraceToString())
            return state
        }
    }

    sealed class CheckResult(val state: TraverserState)

    class SuccessCheck(state: TraverserState): CheckResult(state)
    class UnsuccessfulCheck(state: TraverserState): CheckResult(state)

    private fun nullCheck(
        traverserState: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        term: Term
    ): CheckResult {
        if (term in traverserState.nullCheckedTerms) return SuccessCheck(traverserState)
        if (term is ConstClassTerm) return SuccessCheck(traverserState)
        if (term is StaticClassRefTerm) return SuccessCheck(traverserState)
        if (term.isThis) return SuccessCheck(traverserState)

        val nullityClause = PathClause(
            PathClauseType.NULL_CHECK,
            inst,
            path { (term eq null) equality true }
        )
        return if (nextInstruction is CatchInst) {
            UnsuccessfulCheck(traverserState + nullityClause)
        }
        else {
            SuccessCheck(traverserState + nullityClause.inverse())
        }
    }

    private fun boundsCheck(
        traverserState: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        index: Term,
        length: Term
    ): CheckResult {
        if (index to index in traverserState.boundCheckedTerms) return SuccessCheck(traverserState)
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
            UnsuccessfulCheck(traverserState + zeroClause)
        }
        else {
            SuccessCheck(traverserState + zeroClause.inverse() + lengthClause.inverse())
        }
    }

    private fun typeCheck(
        state: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        term: Term,
        type: KexType
    ): CheckResult {
        if (type !is KexPointer) return SuccessCheck(state)
        val previouslyCheckedType = state.typeCheckedTerms[term]
        val currentlyCheckedType = type.getKfgType(ctx.types)
        if (previouslyCheckedType != null && currentlyCheckedType.isSubtypeOfCached(previouslyCheckedType)) {
            return SuccessCheck(state)
        }

        val typeClause = PathClause(
            PathClauseType.TYPE_CHECK,
            inst,
            path { (term `is` type) equality false }
        )

        return if (nextInstruction is CatchInst) {
            UnsuccessfulCheck(state + typeClause)
        }
        else {
            SuccessCheck(state + typeClause.inverse())
        }
    }

    private fun newArrayBoundsCheck(
        state: TraverserState,
        inst: Instruction,
        nextInstruction: Instruction?,
        index: Term
    ): CheckResult {
        if (index to index in state.boundCheckedTerms) return SuccessCheck(state)

        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality false }
        )
        val noExceptionConstraints = persistentSymbolicState() + zeroClause.inverse()
        val zeroCheckConstraints = persistentSymbolicState() + zeroClause

        if (nextInstruction is CatchInst) {
            return UnsuccessfulCheck(state + zeroCheckConstraints)
        }
        else {
            val res = state + noExceptionConstraints
            return SuccessCheck(res.copy(boundCheckedTerms = res.boundCheckedTerms.add(index to index)) + noExceptionConstraints)
        }
    }

    private fun traverseArrayLoadInst(
        traverserState: TraverserState,
        inst: ArrayLoadInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val res = generate(inst.type.symbolicType)

        if (arrayTerm is NullTerm) {
            return nullCheck(traverserState, inst, nextInstruction, arrayTerm).state
        }

        val clause = StateClause(inst, state { res equality arrayTerm[indexTerm].load() })

        var result = nullCheck(traverserState, inst, nextInstruction, arrayTerm)
        if (result is UnsuccessfulCheck) return result.state

        result = boundsCheck(result.state, inst, nextInstruction, indexTerm, arrayTerm.length())
        if (result is UnsuccessfulCheck) return result.state
        val checkedState = result.state

        return checkedState.copy(
            symbolicState = checkedState.symbolicState + clause,
            valueMap = checkedState.valueMap.put(inst, res)
        )
    }

    private fun traverseArrayStoreInst(
        traverserState: TraverserState,
        inst: ArrayStoreInst,
        nextInstruction: Instruction?
    ): TraverserState? {
        val arrayTerm = traverserState.mkTerm(inst.arrayRef)
        val indexTerm = traverserState.mkTerm(inst.index)
        val valueTerm = traverserState.mkTerm(inst.value)

        if (arrayTerm is NullTerm) {
            return nullCheck(traverserState, inst, nextInstruction, arrayTerm).state
        }

        val clause = StateClause(inst, state { arrayTerm[indexTerm].store(valueTerm) })

        var result = nullCheck(traverserState, inst, nextInstruction, arrayTerm)
        if (result is UnsuccessfulCheck) return result.state

        result = boundsCheck(result.state, inst, nextInstruction, indexTerm, arrayTerm.length())
        if (result is UnsuccessfulCheck) return result.state

        return result.state + clause
    }

    private fun traverseBinaryInst(traverserState: TraverserState, inst: BinaryInst): TraverserState {
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

    private fun traverseBranchInst(
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

        return if (nextInstruction in inst.trueSuccessor) {
            traverserState + trueClause + inst.parent
        } else traverserState + falseClause + inst.parent
    }

    val callResolver: SymbolicCallResolver = DefaultCallResolver(ctx)

    private fun traverseCallInst(
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

        val checkResult = nullCheck(traverserState, inst, nextInstruction, callee)
        if (checkResult is UnsuccessfulCheck) return checkResult.state
        val checkedState = checkResult.state

        val candidate = candidates.find { !it.body.entry.isEmpty && it.body.entry.instructions[0] == nextInstruction }
        return when {
            candidate == null -> {
                var varState = checkedState
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

            else -> processMethodCall(checkedState, inst, nextInstruction, candidate, callee, argumentTerms)
        }
    }

    private fun traverseCastInst(
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

        val checkResult = typeCheck(traverserState, inst, nextInstruction, operandTerm, resultTerm.type)
        if (checkResult is UnsuccessfulCheck) return checkResult.state
        val checkedState = checkResult.state

        return checkedState.copy(
            symbolicState = checkedState.symbolicState + clause,
            valueMap = checkedState.valueMap.put(inst, resultTerm)
        ).copyTermInfo(operandTerm, resultTerm)
    }

    private fun traverseCatchInst(traverserState: TraverserState): TraverserState {
        return traverserState
    }

    private fun traverseCmpInst(
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

    private fun traverseEnterMonitorInst(
        traverserState: TraverserState,
        inst: EnterMonitorInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val monitorTerm = traverserState.mkTerm(inst.owner)
        val clause = StateClause(
            inst,
            state { enterMonitor(monitorTerm) }
        )

        val checkResult = nullCheck(traverserState, inst, nextInstruction, monitorTerm)
        if (checkResult is UnsuccessfulCheck) return checkResult.state
        return checkResult.state + clause
    }

    private fun traverseExitMonitorInst(
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

    private fun traverseFieldLoadInst(
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
            return nullCheck(traverserState, inst, nextInstruction, objectTerm).state
        }

        val res = generate(inst.type.symbolicType)
        val clause = StateClause(
            inst,
            state { res equality objectTerm.field(field.type.symbolicType, field.name).load() }
        )

        val checkResult = nullCheck(traverserState, inst, nextInstruction, objectTerm)
        if (checkResult is UnsuccessfulCheck) return checkResult.state
        val checkedState = checkResult.state

        val newNullChecked = when {
            field.isStatic && field.isFinal -> when (field.defaultValue) {
                null -> checkedState.nullCheckedTerms.add(res)
                ctx.values.nullConstant -> checkedState.nullCheckedTerms
                else -> checkedState.nullCheckedTerms.add(res)
            }

            else -> checkedState.nullCheckedTerms
        }
        return checkedState.copy(
            symbolicState = checkedState.symbolicState + clause,
            valueMap = checkedState.valueMap.put(inst, res),
            nullCheckedTerms = newNullChecked
        )
    }

    private fun traverseFieldStoreInst(
        traverserState: TraverserState,
        inst: FieldStoreInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val objectTerm = when {
            inst.isStatic -> staticRef(inst.field.klass)
            else -> traverserState.mkTerm(inst.owner)
        }

        if (objectTerm is NullTerm) {
            return nullCheck(traverserState, inst, nextInstruction, objectTerm).state
        }

        val valueTerm = traverserState.mkTerm(inst.value)
        val clause = StateClause(
            inst,
            state { objectTerm.field(inst.field.type.symbolicType, inst.field.name).store(valueTerm) }
        )

        val checkResult = nullCheck(traverserState, inst, nextInstruction, objectTerm)
        if (checkResult is UnsuccessfulCheck) return checkResult.state
        val checkedState = checkResult.state

        return checkedState.copy(
            symbolicState = checkedState.symbolicState + clause,
            valueMap = checkedState.valueMap.put(inst, valueTerm)
        )
    }

    private fun traverseInstanceOfInst(
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

    private val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)

    private fun traverseInvokeDynamicInst(
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

    private fun processMethodCall(
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
                val checkResult = typeCheck(traverserState, inst, nextInstruction, callee, candidate.klass.symbolicClass)
                if (checkResult is UnsuccessfulCheck) return checkResult.state
                val checkedState = checkResult.state

                return when {
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
                        checkedState.copy(
                            symbolicState = checkedState.symbolicState + convertClause
                        ).copyTermInfo(callee, newCalleeTerm)
                    }

                    else -> traverserState
                }.copy(
                    valueMap = newValueMap,
                    stackTrace = checkedState.stackTrace.add(
                        SymbolicStackTraceElement(inst.parent.method, inst, checkedState.valueMap)
                    )
                )
            }
        }
    }

    private fun traverseNewArrayInst(
        traverserState: TraverserState,
        inst: NewArrayInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val dimensions = inst.dimensions.map { traverserState.mkTerm(it) }
        val resultTerm = generate(inst.type.symbolicType)
        val clause = StateClause(inst, state { resultTerm.new(dimensions) })

        var result: TraverserState = traverserState
        dimensions.forEach { dimension ->
            val checkResult = newArrayBoundsCheck(traverserState, inst, nextInstruction, dimension)
            if (checkResult is UnsuccessfulCheck) return checkResult.state
            result = checkResult.state
        }

        return result.copy(
            symbolicState = result.symbolicState + clause,
            typeInfo = result.typeInfo.put(resultTerm, inst.type.rtMapped),
            valueMap = result.valueMap.put(inst, resultTerm),
            nullCheckedTerms = result.nullCheckedTerms.add(resultTerm),
            typeCheckedTerms = result.typeCheckedTerms.put(resultTerm, inst.type)
        )
    }

    private fun traverseNewInst(
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

    private fun traversePhiInst(
        traverserState: TraverserState,
        inst: PhiInst
    ): TraverserState {
        val previousBlock = traverserState.blockPath.last { it.method == inst.parent.method }
        val value = traverserState.mkTerm(inst.incomings.getValue(previousBlock))
        return traverserState.copy(
            valueMap = traverserState.valueMap.put(inst, value)
        )
    }

    private fun traverseUnaryInst(
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
            UnaryOpcode.LENGTH -> nullCheck(traverserState, inst, nextInstruction, operandTerm).state
            else -> traverserState
        }

        return result.copy(
            symbolicState = result.symbolicState + clause,
            valueMap = result.valueMap.put(inst, resultTerm)
        )
    }

    private fun traverseJumpInst(
        traverserState: TraverserState,
        inst: JumpInst
    ): TraverserState {
        return traverserState + inst.parent
    }

    private fun traverseReturnInst(
        traverserState: TraverserState,
        inst: ReturnInst
    ): TraverserState {
        val stackTrace = traverserState.stackTrace
        val stackTraceElement = stackTrace.lastOrNull()
        val receiver = stackTraceElement?.instruction
        return when {
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
    }

    private fun traverseSwitchInst(
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

    private fun traverseTableSwitchInst(
        traverserState: TraverserState,
        inst: TableSwitchInst,
        nextInstruction: Instruction?
    ): TraverserState {
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

    private fun traverseThrowInst(
        traverserState: TraverserState,
        inst: ThrowInst,
        nextInstruction: Instruction?
    ): TraverserState {
        val throwableTerm = traverserState.mkTerm(inst.throwable)
        val throwClause = StateClause(
            inst,
            state { `throw`(throwableTerm) }
        )

        val result = nullCheck(traverserState, inst, nextInstruction, throwableTerm)
        if (result is UnsuccessfulCheck) return result.state
        return result.state + throwClause
    }

    private fun traverseUnreachableInst(): TraverserState? = null

    private fun traverseUnknownValueInst(
        inst: UnknownValueInst
    ): TraverserState? {
        return unreachable("Unexpected visit of $inst in symbolic traverser")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun PathClause.inverse(): PathClause = this.copy(
        predicate = this.predicate.inverse(ctx.random)
    )
}