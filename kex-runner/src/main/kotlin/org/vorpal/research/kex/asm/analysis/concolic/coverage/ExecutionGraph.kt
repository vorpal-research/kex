package org.vorpal.research.kex.asm.analysis.concolic.coverage

import com.jetbrains.rd.util.concurrentMapOf
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.NoConcreteInstanceException
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.DefaultSwitchPredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.predicate
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.InstanceOfTerm
import org.vorpal.research.kex.state.term.boolValue
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.transformer.TermCollector
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.PersistentClauseList
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.toPersistentState
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kex.util.next
import org.vorpal.research.kfg.arrayIndexOOBClass
import org.vorpal.research.kfg.classCastClass
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.EmptyUsageContext
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.nullptrClass
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.PredecessorGraph
import org.vorpal.research.kthelper.graph.Viewable
import org.vorpal.research.kthelper.logging.log
import java.util.*

private fun Predicate.reverseBoolCond() = when (this) {
    is EqualityPredicate -> predicate(this.type, this.location) {
        lhv equality !(rhv as ConstBoolTerm).value
    }

    is InequalityPredicate -> predicate(this.type, this.location) {
        lhv inequality !(rhv as ConstBoolTerm).value
    }

    else -> unreachable { log.error("Unexpected predicate in bool cond: $this") }
}

private fun Predicate.reverseSwitchCond(
    predecessors: Set<PersistentSymbolicState>,
    branches: Map<Value, BasicBlock>
): List<Predicate> = when (this) {
    is DefaultSwitchPredicate -> branches.keys
        .mapTo(mutableSetOf()) { (it as IntConstant).value }
        .map {
            predicate(this.type, this.location) {
                cond equality it
            }
        }


    is EqualityPredicate -> {
        val outgoingPaths = branches.toList()
            .groupBy({ it.second }, { it.first })
            .map { it.value.mapTo(mutableSetOf()) { const -> (const as IntConstant).value } }

        val equivalencePaths = mutableMapOf<Int, Set<Int>>()
        for (set in outgoingPaths) {
            for (value in set) {
                equivalencePaths[value] = set
            }
        }

        val visitedCandidates = predecessors
            .map { it.path.last().predicate }
            .filterIsInstance<EqualityPredicate>()
            .mapTo(mutableSetOf()) { it.rhv.numericValue }

        val candidates = run {
            val currentRange = branches.keys.map { (it as IntConstant).value }.toMutableSet()
            for (candidate in visitedCandidates) {
                val visited: Set<Int> = equivalencePaths[candidate] ?: emptySet()
                currentRange.removeAll(visited)
            }
            currentRange
        }

        candidates.map {
            predicate(type, location) {
                lhv equality it
            }
        }
    }

    else -> unreachable { log.error("Unexpected predicate in switch clause: $this") }
}


sealed class Vertex(
    val type: String,
    val instruction: Instruction
) : PredecessorGraph.PredecessorVertex<Vertex> {
    companion object {
        const val STATE = "STATE"
    }

    private val _predecessors = mutableSetOf<Vertex>()
    private val _successors = mutableSetOf<Vertex>()

    override val predecessors: Set<Vertex>
        get() = _predecessors
    override val successors: Set<Vertex>
        get() = _successors

    fun linkDown(other: Vertex) {
        _successors += other
        other._predecessors += this
    }

    fun linkUp(other: Vertex) {
        _predecessors += other
        other._successors += this
    }

    override fun toString(): String {
        return "Vertex($type, ${instruction.print()})"
    }
}

class StateVertex(
    instruction: Instruction
) : Vertex(STATE, instruction)

class PathVertex(
    val pathType: PathClauseType,
    instruction: Instruction
) : Vertex(pathType.name, instruction) {
    val states = concurrentMapOf<PersistentClauseList, MutableSet<PersistentSymbolicState>>()
    private val visitedPrefixes = Collections.newSetFromMap(concurrentMapOf<PersistentClauseList, Boolean>())

    fun addStateAndProduceCandidates(
        ctx: ExecutionContext,
        state: PersistentSymbolicState
    ): List<PersistentSymbolicState> {
        val prefix = state.clauses.dropLast(1)
        val condition = state.path.last()
        val prefixStates = states.getOrPut(prefix, ::mutableSetOf).also {
            it += state
        }
        return when (prefix) {
            in visitedPrefixes -> emptyList()
            else -> {
                visitedPrefixes += prefix
                val reversedConditions = when (condition.type) {
                    PathClauseType.NULL_CHECK -> listOf(condition.copy(predicate = condition.predicate.reverseBoolCond()))
                    PathClauseType.TYPE_CHECK -> listOf(condition.copy(predicate = condition.predicate.reverseBoolCond()))
                    PathClauseType.BOUNDS_CHECK -> listOf(condition.copy(predicate = condition.predicate.reverseBoolCond()))
                    PathClauseType.CONDITION_CHECK -> when (val inst = condition.instruction) {
                        is BranchInst -> listOf(condition.copy(predicate = condition.predicate.reverseBoolCond()))
                        is SwitchInst -> condition.predicate.reverseSwitchCond(prefixStates, inst.branches).map {
                            condition.copy(predicate = it)
                        }

                        is TableSwitchInst -> {
                            val branches = inst.range.let { range ->
                                range.associateWith { inst.branches[it - range.first] }
                                    .mapKeys { ctx.values.getInt(it.key) }
                            }
                            condition.predicate.reverseSwitchCond(prefixStates, branches).map {
                                condition.copy(predicate = it)
                            }
                        }

                        else -> unreachable { log.error("Unexpected predicate in clause $inst") }
                    }

                    PathClauseType.OVERLOAD_CHECK -> {
                        val excludeClasses = prefixStates
                            .map { it.path.last().predicate }
                            .flatMap { TermCollector.getFullTermSet(it).filterIsInstance<InstanceOfTerm>() }
                            .map { it.checkedType.getKfgType(ctx.types) }
                            .filterIsInstance<ClassType>()
                            .mapTo(mutableSetOf()) { it.klass }

                        try {
                            val lhv = condition.predicate.operands[0] as InstanceOfTerm
                            val termType = lhv.operand.type.getKfgType(ctx.types)
                            val allCandidates = instantiationManager.getAll(termType, ctx.accessLevel, excludeClasses)
                                .filterNot { it.isKexRt }
                            // TODO: add proper prioritization
                            val prioritizedCandidates = allCandidates.shuffled(ctx.random).take(5)
                            prioritizedCandidates.map {
                                if (it is ArrayType) {
                                    val a = 10
                                }
                                condition.copy(predicate = path(instruction.location) {
                                    (lhv.operand `is` it.kexType) equality true
                                })
                            }
                        } catch (e: NoConcreteInstanceException) {
                            emptyList()
                        }
                    }
                }
                reversedConditions.map {
                    persistentSymbolicState(
                        prefix,
                        state.path.dropLast(1) + it,
                        state.concreteTypes,
                        state.concreteValues,
                        state.termMap
                    )
                }
            }
        }
    }
}

private fun PersistentSymbolicState.findExceptionHandlerInst(type: Type): Instruction? {
    val currentClause = path.last()
    val stackTrace = listOf(currentClause.instruction) + stackTrace.mapNotNull { it.first }
    var result: Instruction? = null
    stackTrace@ for (inst in stackTrace) {
        for (handler in inst.parent.handlers) {
            if (type.isSubtypeOfCached(handler.exception)) {
                result = handler.first()
                break@stackTrace
            }
        }
    }
    return result
}

class CandidateState(
    val method: Method,
    val state: PersistentSymbolicState
) {
    val nextInstruction: Instruction?

    init {
        val cm = method.cm
        val currentClause = state.path.last()
        nextInstruction = when (currentClause.type) {
            PathClauseType.NULL_CHECK -> when (currentClause.predicate.operands[1].boolValue) {
                true -> state.findExceptionHandlerInst(cm.nullptrClass.asType)
                false -> currentClause.instruction.next
            }

            PathClauseType.TYPE_CHECK -> when (currentClause.predicate.operands[1].boolValue) {
                true -> currentClause.instruction.next
                false -> state.findExceptionHandlerInst(cm.classCastClass.asType)
            }

            PathClauseType.BOUNDS_CHECK -> when (currentClause.predicate.operands[1].boolValue) {
                true -> currentClause.instruction.next
                false -> state.findExceptionHandlerInst(cm.arrayIndexOOBClass.asType)
            }

            PathClauseType.OVERLOAD_CHECK -> {
                val checkedType =
                    (currentClause.predicate.operands[0] as InstanceOfTerm).checkedType.getKfgType(cm.type)
                val callInst = currentClause.instruction as CallInst
                when (checkedType) {
                    is ClassType -> {
                        val calledMethod = checkedType.klass.getMethod(
                            callInst.method.name,
                            callInst.method.returnType,
                            *callInst.method.argTypes.toTypedArray()
                        )
                        when {
                            calledMethod.hasBody -> calledMethod.body.entry.first()
                            else -> callInst.next
                        }
                    }

                    else -> callInst.next
                }
            }

            PathClauseType.CONDITION_CHECK -> when (val inst = currentClause.instruction) {
                is BranchInst -> when (currentClause.predicate.operands[1].boolValue) {
                    true -> inst.trueSuccessor.first()
                    false -> inst.falseSuccessor.first()
                }

                is SwitchInst -> {
                    val numericValue = currentClause.predicate.operands[1].numericValue
                    var result: Instruction? = null
                    for ((key, branch) in inst.branches) {
                        if (numericValue == (key as IntConstant).value) {
                            result = branch.first()
                        }
                    }
                    if (result == null) {
                        result = inst.default.first()
                    }
                    result
                }

                is TableSwitchInst -> {
                    val numericValue = currentClause.predicate.operands[1].numericValue
                    var result: Instruction? = null
                    for (key in inst.range) {
                        if (numericValue == key) {
                            result = inst.branches[key - inst.range.first].first()
                        }
                    }
                    if (result == null) {
                        result = inst.default.first()
                    }
                    result
                }

                else -> unreachable { log.error("Unexpected instruction in condition check: $inst") }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CandidateState

        return state == other.state
    }

    override fun hashCode(): Int {
        return state.hashCode()
    }
}


class ExecutionGraph(
    val ctx: ExecutionContext,
    val targets: Set<Method>,
    val isCovered: (Instruction) -> Boolean
) : PredecessorGraph<Vertex>, Viewable {

    companion object {
        const val DEFAULT_SCORE = 10_000L
    }

    private val root: Vertex = StateVertex(ctx.cm.instruction.getUnreachable(EmptyUsageContext))
    private val innerNodes = concurrentMapOf<Pair<String, Instruction>, Vertex>().also {
        it["STATE" to root.instruction] = root
    }

    val candidates = CandidateSet(ctx)

    override val entry: Vertex
        get() = root

    override val nodes: Set<Vertex>
        get() = innerNodes.values.toSet()

    inner class CandidateSet(val ctx: ExecutionContext) : Iterable<CandidateState> {
        private var isValid = true
        private var totalScore: Long = 0L
        private val candidates = Collections.newSetFromMap(concurrentMapOf<CandidateState, Boolean>())
//        private val queue = PriorityQueue<CandidateState>(compareBy { -it.score() })

        override fun iterator(): Iterator<CandidateState> = candidates.iterator()

        fun isEmpty() = candidates.isEmpty()

        fun addAll(newCandidates: Collection<CandidateState>) {
            candidates.addAll(newCandidates)
            newCandidates.forEach { totalScore += it.score() }
//            queue.addAll(newCandidates)
        }

        private fun remove(candidateState: CandidateState) {
            if (candidates.remove(candidateState)) {
                totalScore -= candidateState.score()
            }
        }

        fun invalidate() {
            isValid = false
        }

        private fun recomputeScores() {
            totalScore = 0L
            candidates.forEach { totalScore += it.score() }
//            queue.clear()
//            queue.addAll(candidates)
            isValid = true
        }

        fun nextCandidate(): CandidateState {
            if (!isValid) recomputeScores()

//            return queue.poll()!!.also { remove(it) }
//
            val random = ctx.random.nextLong(totalScore)
            var current = 0L
            for (state in candidates) {
                current += state.score()
                if (random < current) {
                    remove(state)
                    return state
                }
            }
            return unreachable { log.error("Unexpected error") }
        }
    }

    private fun CandidateState.score(): Long {
        var score = 1L
        val stackTrace = this.state.stackTrace
        if (nextInstruction != null) {
            score += DEFAULT_SCORE
            if (nextInstruction.parent.method in targets) {
                score += DEFAULT_SCORE
            }
            if (!isCovered(nextInstruction)) {
                score += DEFAULT_SCORE
            }
//            val distanceToTarget = stackTrace.size - ((stackTrace.withIndex().firstOrNull { it.value.second in targets }?.index ?: 0) + 1)
//            val distanceToTargetScore = (DEFAULT_SCORE.toDouble() / 2.0.pow(distanceToTarget)).toLong()
//            score += distanceToTargetScore
//            if (!isCovered(nextInstruction)) {
//                score += distanceToTargetScore
//            }
        }
        return score
    }

    fun addTrace(method: Method, executionResult: ExecutionCompletedResult) = synchronized(this) {
        var prevVertex = root
        var clauseIndex = 0
        var pathIndex = 0
        val symbolicState = executionResult.symbolicState.toPersistentState()
        for (clause in symbolicState.clauses) {
            ++clauseIndex
            if (clause is PathClause) ++pathIndex

            val type = when (clause) {
                is PathClause -> clause.type.toString()
                else -> Vertex.STATE
            }
            val currentVertex = innerNodes.getOrPut(type to clause.instruction) {
                candidates.invalidate()
                when (clause) {
                    is PathClause -> PathVertex(clause.type, clause.instruction)
                    else -> StateVertex(clause.instruction)
                }
            }

            if (clause is PathClause) {
                currentVertex as PathVertex
                candidates.addAll(
                    currentVertex.addStateAndProduceCandidates(
                        ctx, persistentSymbolicState(
                            symbolicState.clauses.subState(0, clauseIndex),
                            symbolicState.path.subPath(0, pathIndex),
                            symbolicState.concreteTypes,
                            symbolicState.concreteValues,
                            symbolicState.termMap
                        )
                    ).mapTo(mutableSetOf()) { CandidateState(method, it) }
                )
            }

            prevVertex.linkDown(currentVertex)
            prevVertex = currentVertex
        }
    }

    override val graphView: List<GraphView>
        get() {
            val graphNodes = mutableMapOf<Vertex, GraphView>()

            var i = 0
            for (vertex in nodes) {
                graphNodes[vertex] = GraphView("${i++}", "$vertex".replace("\"", "\\\"")) {
                    it.setColor(info.leadinglight.jdot.enums.Color.X11.black)
                }
            }

            for (vertex in nodes) {
                val current = graphNodes.getValue(vertex)
                for (child in vertex.successors) {
                    current.addSuccessor(graphNodes.getValue(child))
                }
            }

            return graphNodes.values.toList()
        }
}
