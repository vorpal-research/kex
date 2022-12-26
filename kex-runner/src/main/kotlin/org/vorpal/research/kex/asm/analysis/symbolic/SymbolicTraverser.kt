package org.vorpal.research.kex.asm.analysis.symbolic

import kotlinx.collections.immutable.*
import kotlinx.coroutines.yield
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.annotations.AnnotationManager
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexRtManager.isJavaRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.concreteParameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.inverse
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kex.state.term.TermFactory
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.tryOrNull
import java.util.concurrent.CancellationException

class TraverserState(
    val symbolicState: PersistentSymbolicState,
    val valueMap: PersistentMap<Value, Term>,
    val stackTrace: PersistentList<Pair<Method, Instruction>>,
    val typeInfo: PersistentMap<Term, KexType>,
    val blockPath: PersistentList<BasicBlock>,
) {

    fun copy(
        state: PersistentSymbolicState = this.symbolicState,
        valueMap: PersistentMap<Value, Term> = this.valueMap,
        stackTrace: PersistentList<Pair<Method, Instruction>> = this.stackTrace,
        typeInfo: PersistentMap<Term, KexType> = this.typeInfo,
        blockPath: PersistentList<BasicBlock> = this.blockPath,
    ): TraverserState = TraverserState(state, valueMap, stackTrace, typeInfo, blockPath)
}

class SymbolicTraverser(
    val ctx: ExecutionContext,
    val rootMethod: Method
) : TermBuilder(), MethodVisitor {
    override val cm: ClassManager
        get() = ctx.cm

    private val pathSelector: PathSelector = DequePathSelector()
    private val callResolver: CallResolver = DefaultCallResolver()
    private var currentState: TraverserState? = null
    private var testIndex = 0

    fun TraverserState.mkValue(value: Value): Term = when (value) {
        is Constant -> const(value)
        else -> valueMap.getValue(value)
    }

    override fun cleanup() {}

    suspend fun analyze() {
        try {
            if (rootMethod.isStaticInitializer || !rootMethod.hasBody) return
            if (!MethodManager.canBeImpacted(rootMethod, ctx.accessLevel)) return

            log.debug { "Processing method $rootMethod" }
            log.debug { rootMethod.print() }

            processMethod(rootMethod)
            log.debug { "Method $rootMethod processing is finished normally" }
        } catch (e: CancellationException) {
            log.warn { "Method $rootMethod processing is finished with timeout" }
            throw e
        }
    }

    private suspend fun processMethod(method: Method) {
        val initialArguments = buildMap {
            this[this@SymbolicTraverser.values.getThis(method.klass)] = `this`(method.klass.kexType)
            for ((index, type) in method.argTypes.withIndex()) {
                this[this@SymbolicTraverser.values.getArgument(index, method, type)] = arg(type.kexType, index)
            }
        }
        pathSelector.add(
            TraverserState(
                persistentSymbolicState(),
                initialArguments.toPersistentMap(),
                persistentListOf(),
                persistentMapOf(),
                persistentListOf(),
            ),
            method.body.entry
        )

        while (pathSelector.hasNext()) {
            val (currentState, currentBlock) = pathSelector.next()
            this.currentState = currentState
            visitBasicBlock(currentBlock)
            yield()
        }
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        traverseBlock(bb, startIndex = 0)
    }

    private fun traverseBlock(bb: BasicBlock, startIndex: Int = 0) {
        for (index in startIndex..bb.instructions.lastIndex) {
            val inst = bb.instructions[index]
            visitInstruction(inst)
        }
    }


    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        currentState?.let {
            var traverserState = it
            val arrayTerm = traverserState.mkValue(inst.arrayRef)
            val indexTerm = traverserState.mkValue(inst.index)
            val res = generate(inst.type.kexType)

            traverserState = nullabilityCheck(traverserState, inst, arrayTerm)
            traverserState = boundsCheck(traverserState, inst, indexTerm, arrayTerm.length())

            val clause = StateClause(inst, state { res equality arrayTerm[indexTerm] })
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, res)
            )
        }
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        currentState?.let {
            var traverserState = it
            val arrayTerm = traverserState.mkValue(inst.arrayRef)
            val indexTerm = traverserState.mkValue(inst.index)
            val valueTerm = traverserState.mkValue(inst.value)

            traverserState = nullabilityCheck(traverserState, inst, arrayTerm)
            traverserState = boundsCheck(traverserState, inst, indexTerm, arrayTerm.length())

            val clause = StateClause(inst, state { arrayTerm[indexTerm].store(valueTerm) })
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                )
            )
        }
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        currentState?.let { traverserState ->
            val lhvTerm = traverserState.mkValue(inst.lhv)
            val rhvTerm = traverserState.mkValue(inst.rhv)
            val resultTerm = generate(inst.type.kexType)

            val clause = StateClause(
                inst,
                state { resultTerm equality lhvTerm.apply(resultTerm.type, inst.opcode, rhvTerm) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    override fun visitBranchInst(inst: BranchInst) {
        currentState?.let { traverserState ->
            val condTerm = traverserState.mkValue(inst.cond)

            val trueClause = PathClause(
                PathClauseType.CONDITION_CHECK,
                inst,
                path { condTerm equality true }
            )
            val falseClause = trueClause.copy(predicate = trueClause.predicate.inverse())

            pathSelector += traverserState.copy(
                state = traverserState.symbolicState.copy(
                    path = traverserState.symbolicState.path.add(trueClause)
                ),
                blockPath = traverserState.blockPath.add(inst.parent)
            ) to inst.trueSuccessor

            pathSelector += traverserState.copy(
                state = traverserState.symbolicState.copy(
                    path = traverserState.symbolicState.path.add(falseClause)
                ),
                blockPath = traverserState.blockPath.add(inst.parent)
            ) to inst.falseSuccessor
        }
        currentState = null
    }

    override fun visitCallInst(inst: CallInst) {
        currentState?.let {
            var traverserState = it
            val callee = when {
                inst.isStatic -> null
                else -> traverserState.mkValue(inst.callee)
            }
            val argumentTerms = inst.args.map { traverserState.mkValue(it) }
            if (!inst.isStatic) {
                traverserState = nullabilityCheck(traverserState, inst, callee!!)
            }

            val candidates = callResolver.resolve(traverserState, inst)
            for (candidate in candidates) {
                val newValueMap = traverserState.valueMap.builder().let { builder ->
                    if (callee != null) builder[values.getThis(candidate.klass)] = callee
                    for ((index, type) in candidate.argTypes.withIndex()) {
                        builder[values.getArgument(index, candidate, type)] = argumentTerms[index]
                    }
                    builder.build()
                }
                if (callee != null) {
                    traverserState = typeCheck(traverserState, inst, callee, candidate.klass.kexType)
                }
                val newState = traverserState.copy(
                    state = traverserState.symbolicState,
                    valueMap = newValueMap,
                    stackTrace = traverserState.stackTrace.add(inst.parent.method to inst)
                )
                pathSelector.add(
                    newState, candidate.body.entry
                )
            }
            currentState = when {
                candidates.isEmpty() -> {
                    val receiver = when {
                        inst.isNameDefined -> {
                            val res = generate(inst.type.kexType)
                            traverserState = traverserState.copy(
                                valueMap = traverserState.valueMap.put(inst, res)
                            )
                            res
                        }

                        else -> null
                    }
                    val callClause = StateClause(
                        inst, state {
                            val callTerm = when (callee) {
                                null -> TermFactory.getCall(inst.method, argumentTerms)
                                else -> callee.call(inst.method, argumentTerms)
                            }
                            receiver?.call(callTerm) ?: call(callTerm)
                        }
                    )
                    traverserState.copy(
                        state = traverserState.symbolicState.copy(
                            clauses = traverserState.symbolicState.clauses.add(callClause)
                        )
                    )
                }
                else -> {
                    null
                }
            }
        }
    }

    override fun visitCastInst(inst: CastInst) {
        currentState?.let {
            var traverserState = it
            val operandTerm = traverserState.mkValue(inst.operand)
            val resultTerm = generate(inst.type.kexType)

            traverserState = typeCheck(traverserState, inst, operandTerm, resultTerm.type)
            val clause = StateClause(
                inst,
                state { resultTerm equality (operandTerm `as` resultTerm.type) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    override fun visitCatchInst(inst: CatchInst) {}

    override fun visitCmpInst(inst: CmpInst) {
        currentState?.let { traverserState ->
            val lhvTerm = traverserState.mkValue(inst.lhv)
            val rhvTerm = traverserState.mkValue(inst.rhv)
            val resultTerm = generate(inst.type.kexType)

            val clause = StateClause(
                inst,
                state { resultTerm equality lhvTerm.apply(inst.opcode, rhvTerm) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
        currentState?.let {
            var traverserState = it
            val monitorTerm = traverserState.mkValue(inst.owner)

            traverserState = nullabilityCheck(traverserState, inst, monitorTerm)
            val clause = StateClause(
                inst,
                state { enterMonitor(monitorTerm) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                )
            )
        }
    }

    override fun visitExitMonitorInst(inst: ExitMonitorInst) {
        currentState?.let { traverserState ->
            val monitorTerm = traverserState.mkValue(inst.owner)

            val clause = StateClause(
                inst,
                state { exitMonitor(monitorTerm) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                )
            )
        }
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        currentState?.let {
            var traverserState = it
            val objectTerm = traverserState.mkValue(inst.owner)
            val res = generate(inst.type.kexType)

            traverserState = nullabilityCheck(traverserState, inst, objectTerm)

            val clause = StateClause(
                inst,
                state { res equality objectTerm.field(inst.field.type.kexType, inst.field.name) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, res)
            )
        }
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        currentState?.let {
            var traverserState = it
            val objectTerm = traverserState.mkValue(inst.owner)
            val valueTerm = traverserState.mkValue(inst.value)

            traverserState = nullabilityCheck(traverserState, inst, objectTerm)

            val clause = StateClause(
                inst,
                state { objectTerm.field(inst.field.type.kexType, inst.field.name).store(valueTerm) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, valueTerm)
            )
        }
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        currentState?.let { traverserState ->
            val operandTerm = traverserState.mkValue(inst.operand)
            val resultTerm = generate(inst.type.kexType)

            val clause = StateClause(
                inst,
                state { resultTerm equality (operandTerm `is` inst.targetType.kexType) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    override fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {
        TODO()
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        currentState?.let {
            var traverserState = it
            val dimensions = inst.dimensions.map { traverserState.mkValue(it) }
            val resultTerm = generate(inst.type.kexType)

            dimensions.forEach {
                traverserState = newArrayBoundsCheck(traverserState, inst, it)
            }
            val clause = StateClause(
                inst,
                state { resultTerm.new(dimensions) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    override fun visitNewInst(inst: NewInst) {
        currentState?.let { traverserState ->
            val resultTerm = generate(inst.type.kexType)

            val clause = StateClause(
                inst,
                state { resultTerm.new() }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    override fun visitPhiInst(inst: PhiInst) {
        currentState?.let { traverserState ->
            currentState = traverserState.copy(
                valueMap = traverserState.valueMap.put(
                    inst,
                    traverserState.mkValue(inst.incomings.getValue(traverserState.blockPath.last()))
                )
            )
        }
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        currentState?.let { traverserState ->
            val operandTerm = traverserState.mkValue(inst.operand)
            val resultTerm = generate(inst.type.kexType)

            val clause = StateClause(
                inst,
                state { resultTerm equality operandTerm.apply(inst.opcode) }
            )
            currentState = traverserState.copy(
                state = traverserState.symbolicState.copy(
                    clauses = traverserState.symbolicState.clauses.add(clause)
                ),
                valueMap = traverserState.valueMap.put(inst, resultTerm)
            )
        }
    }

    override fun visitJumpInst(inst: JumpInst) {
        currentState?.let { traverserState ->
            pathSelector += traverserState.copy(
                blockPath = traverserState.blockPath.add(inst.parent)
            ) to inst.successor
        }
        currentState = null
    }

    override fun visitReturnInst(inst: ReturnInst) {
        currentState?.let { traverserState ->
            val stackTrace = traverserState.stackTrace
            val receiver = stackTrace.last().second
            currentState = when {
                inst.hasReturnValue && receiver.isNameDefined -> {
                    val returnTerm = traverserState.mkValue(inst.returnValue)
                    traverserState.copy(
                        valueMap = traverserState.valueMap.put(receiver, returnTerm),
                        stackTrace = stackTrace.removeAt(stackTrace.lastIndex)
                    )
                }

                else -> traverserState.copy(
                    stackTrace = stackTrace.removeAt(stackTrace.lastIndex)
                )
            }
            val nextInst = receiver.parent.indexOf(receiver) + 1
            traverseBlock(receiver.parent, nextInst)
        }
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        currentState?.let { traverserState ->
            val key = traverserState.mkValue(inst.key)
            for ((value, branch) in inst.branches) {
                val path = PathClause(
                    PathClauseType.CONDITION_CHECK,
                    inst,
                    path { (key eq traverserState.mkValue(value)) equality true }
                )
                pathSelector += traverserState.run {
                    copy(
                        state = symbolicState.copy(
                            path = symbolicState.path.add(path)
                        ),
                        blockPath = blockPath.add(inst.parent)
                    )
                } to branch
            }
            val defaultPath = PathClause(
                PathClauseType.CONDITION_CHECK,
                inst,
                path { key `!in` inst.operands.map { traverserState.mkValue(it) } }
            )
            pathSelector += traverserState.run {
                copy(
                    state = symbolicState.copy(
                        path = symbolicState.path.add(defaultPath)
                    ),
                    blockPath = blockPath.add(inst.parent)
                )
            } to inst.default
        }
        currentState = null
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        currentState?.let { traverserState ->
            val key = traverserState.mkValue(inst.index)
            val min = inst.range.first
            for ((index, branch) in inst.branches.withIndex()) {
                val path = PathClause(
                    PathClauseType.CONDITION_CHECK,
                    inst,
                    path { (key eq const(min + index)) equality true }
                )
                pathSelector += traverserState.run {
                    copy(
                        state = symbolicState.copy(
                            path = symbolicState.path.add(path)
                        ),
                        blockPath = blockPath.add(inst.parent)
                    )
                } to branch
            }
            val defaultPath = PathClause(
                PathClauseType.CONDITION_CHECK,
                inst,
                path { key `!in` inst.range.map { const(it) } }
            )
            pathSelector += traverserState.run {
                copy(
                    state = symbolicState.copy(
                        path = symbolicState.path.add(defaultPath)
                    ),
                    blockPath = blockPath.add(inst.parent)
                )
            } to inst.default
        }
        currentState = null
    }

    override fun visitThrowInst(inst: ThrowInst) {
        currentState?.let {
            var traverserState = it
            val persistentState = traverserState.symbolicState
            val throwableTerm = traverserState.mkValue(inst.throwable)

            traverserState = nullabilityCheck(traverserState, inst, throwableTerm)
            val throwClause = StateClause(
                inst,
                state { `throw`(throwableTerm) }
            )
            checkExceptionAndReport(
                traverserState.copy(
                    state = persistentState.copy(
                        clauses = persistentState.clauses.add(throwClause)
                    )
                ),
                inst,
                throwableTerm
            )
        }
        currentState = null
    }

    override fun visitUnreachableInst(inst: UnreachableInst) {
        unreachable<Unit>("Unexpected visit of $inst in symbolic traverser")
    }

    override fun visitUnknownValueInst(inst: UnknownValueInst) {
        unreachable<Unit>("Unexpected visit of $inst in symbolic traverser")
    }

    private fun nullabilityCheck(state: TraverserState, inst: Instruction, term: Term): TraverserState {
        val persistentState = state.symbolicState
        val nullityClause = PathClause(
            PathClauseType.NULL_CHECK,
            inst,
            path { (term eq null) equality true }
        )
        checkExceptionAndReport(
            state.copy(
                state = persistentState.copy(
                    path = persistentState.path.add(nullityClause)
                )
            ),
            inst,
            generate(cm["java/lang/NullPointerException"].kexType)
        )
        return state.copy(
            state = persistentState.copy(
                path = persistentState.path.add(
                    nullityClause.copy(predicate = nullityClause.predicate.inverse())
                )
            )
        )
    }

    private fun boundsCheck(state: TraverserState, inst: Instruction, index: Term, length: Term): TraverserState {
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
                state = persistentState.copy(
                    path = persistentState.path.add(zeroClause)
                )
            ),
            inst,
            generate(cm["java/lang/ArrayIndexOutOfBoundsException"].kexType)
        )
        checkExceptionAndReport(
            state.copy(
                state = persistentState.copy(
                    path = persistentState.path.add(lengthClause)
                )
            ),
            inst,
            generate(cm["java/lang/ArrayIndexOutOfBoundsException"].kexType)
        )
        return state.copy(
            state = persistentState.copy(
                path = persistentState.path.add(
                    zeroClause.copy(predicate = zeroClause.predicate.inverse())
                ).add(
                    lengthClause.copy(predicate = lengthClause.predicate.inverse())
                )
            )
        )
    }

    private fun newArrayBoundsCheck(state: TraverserState, inst: Instruction, index: Term): TraverserState {
        val persistentState = state.symbolicState
        val zeroClause = PathClause(
            PathClauseType.BOUNDS_CHECK,
            inst,
            path { (index ge 0) equality false }
        )
        checkExceptionAndReport(
            state.copy(
                state = persistentState.copy(
                    path = persistentState.path.add(zeroClause)
                )
            ),
            inst,
            generate(cm["java/lang/NegativeArraySizeException"].kexType)
        )
        return state.copy(
            state = persistentState.copy(
                path = persistentState.path.add(
                    zeroClause.copy(predicate = zeroClause.predicate.inverse())
                )
            )
        )
    }

    private fun typeCheck(state: TraverserState, inst: Instruction, term: Term, type: KexType): TraverserState {
        val persistentState = state.symbolicState
        val typeClause = PathClause(
            PathClauseType.TYPE_CHECK,
            inst,
            path { (term `is` type) equality false }
        )
        checkExceptionAndReport(
            state.copy(
                state = persistentState.copy(
                    path = persistentState.path.add(typeClause)
                )
            ),
            inst,
            generate(cm["java/lang/ClassCastException"].kexType)
        )
        return state.copy(
            state = persistentState.copy(
                path = persistentState.path.add(
                    typeClause.copy(predicate = typeClause.predicate.inverse())
                )
            )
        )
    }

    private fun checkExceptionAndReport(
        state: TraverserState,
        inst: Instruction,
        throwable: Term
    ) {
        val throwableType = throwable.type.getKfgType(types)
        val catcher: BasicBlock? = state.run {
            var catcher = inst.parent.handlers.firstOrNull { throwableType.isSubtypeOf(it.exception) }
            if (catcher != null) return@run catcher
            for (i in stackTrace.indices.reversed()) {
                val block = stackTrace[i].second.parent
                catcher = block.handlers.firstOrNull { throwableType.isSubtypeOf(it.exception) }
                if (catcher != null) return@run catcher
            }
            null
        }
        when {
            catcher != null -> {
                val catchInst = catcher.instructions.first { it is CatchInst } as CatchInst
                pathSelector += state.copy(
                    valueMap = state.valueMap.put(catchInst, throwable),
                    blockPath = state.blockPath.add(inst.parent)
                ) to catcher
            }

            else -> {
                val params = check(rootMethod, state.symbolicState)
                if (params != null) {
                    report(params, "Throw${throwableType}")
                }
            }
        }
    }

    private fun report(parameters: Parameters<Descriptor>, testPostfix: String = "") {
        val generator = UnsafeGenerator(ctx, rootMethod, rootMethod.klassName + testPostfix + testIndex++)
        generator.generate(parameters)
        generator.emit()
    }

    private fun check(method: Method, state: SymbolicState): Parameters<Descriptor>? {
        val checker = Checker(method, ctx, PredicateStateAnalysis(cm))
        val query = state.path.asState()
        val concreteTypeInfo = state.concreteValueMap
            .mapValues { it.value.type }
            .filterValues { it.isJavaRt }
            .mapValues { it.value.rtMapped }
            .toTypeMap()
        val preparedState = prepareState(method, state.clauses.asState() + query, concreteTypeInfo)
        log.debug { "Prepared state: $preparedState" }
        val result = checker.check(preparedState)
        if (result !is Result.SatResult) return null

        return tryOrNull {
            generateFinalDescriptors(method, ctx, result.model, checker.state)
                .concreteParameters(ctx.cm, ctx.accessLevel, ctx.random).also {
                    log.debug { "Generated params:\n$it" }
                }
        }
    }

    private fun prepareState(
        method: Method,
        state: PredicateState,
        typeMap: TypeInfoMap = emptyMap<Term, KexType>().toTypeMap()
    ): PredicateState = transform(state) {
        +KexRtAdapter(cm)
        +RecursiveInliner(PredicateStateAnalysis(cm)) { index, psa ->
            ConcolicInliner(
                ctx,
                typeMap,
                psa,
                inlineSuffix = "inlined",
                inlineIndex = index,
                kexRtOnly = false
            )
        }
        +RecursiveInliner(PredicateStateAnalysis(cm)) { index, psa ->
            ConcolicInliner(
                ctx,
                typeMap,
                psa,
                inlineSuffix = "rt.inlined",
                inlineIndex = index,
                kexRtOnly = true
            )
        }
        +ClassAdapter(cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +ReflectionInfoAdapter(method, ctx.loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ClassMethodAdapter(method.cm)
        +ConstEnumAdapter(ctx)
        +ConstStringAdapter(method.cm.type)
        +StringMethodAdapter(ctx.cm)
        +ConcolicArrayLengthAdapter()
        +FieldNormalizer(method.cm)
        +TypeNameAdapter(ctx.types)
    }
}
