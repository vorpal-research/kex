package org.vorpal.research.kex.asm.analysis.concolic.coverage

import com.jetbrains.rd.util.concurrentMapOf
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.NoConcreteInstanceException
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.DefaultSwitchPredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.predicate
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.InstanceOfTerm
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.transformer.TermCollector
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.PersistentPathCondition
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.toPersistentState
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.EmptyUsageContext
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.type.ClassType
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
                currentRange.removeAll(equivalencePaths[candidate]!!)
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
    val states = concurrentMapOf<PersistentPathCondition, MutableSet<PersistentSymbolicState>>()
    private val visitedPrefixes = Collections.newSetFromMap(concurrentMapOf<PersistentPathCondition, Boolean>())

    fun addStateAndProduceCandidates(
        ctx: ExecutionContext,
        state: PersistentSymbolicState
    ): List<PersistentSymbolicState> {
        val prefix = state.path.dropLast(1)
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
                            instantiationManager.getAll(termType, ctx.accessLevel, excludeClasses).map {
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
                        state.clauses.dropLast(1),
                        prefix + it,
                        state.concreteTypes,
                        state.concreteValues,
                        state.termMap
                    )
                }
            }
        }
    }
}


class ExecutionGraph(val ctx: ExecutionContext) : PredecessorGraph<Vertex>, Viewable {
    private val root: Vertex = StateVertex(ctx.cm.instruction.getUnreachable(EmptyUsageContext))
    private val innerNodes = concurrentMapOf<Pair<String, Instruction>, Vertex>().also {
        it["STATE" to root.instruction] = root
    }

    val candidates = concurrentMapOf<PersistentSymbolicState, Method>()

    override val entry: Vertex
        get() = root

    override val nodes: Set<Vertex>
        get() = innerNodes.values.toSet()

    fun addTrace(method: Method, executionResult: ExecutionCompletedResult) = synchronized(this) {
        var prevVertex = root
        var clauseIndex = 0
        var pathIndex = 0
        val symbolicState = executionResult.symbolicState.toPersistentState()
        var totalNewCandidates = 0
        for (clause in symbolicState.clauses) {
            ++clauseIndex
            if (clause is PathClause) ++pathIndex

            val type = when (clause) {
                is PathClause -> clause.type.toString()
                else -> Vertex.STATE
            }
            val currentVertex = innerNodes.getOrPut(type to clause.instruction) {
                when (clause) {
                    is PathClause -> PathVertex(clause.type, clause.instruction)
                    else -> StateVertex(clause.instruction)
                }
            }

            if (clause is PathClause) {
                currentVertex as PathVertex
                val newCands = currentVertex.addStateAndProduceCandidates(
                    ctx, persistentSymbolicState(
                        symbolicState.clauses.subState(0, clauseIndex),
                        symbolicState.path.subPath(0, pathIndex),
                        symbolicState.concreteTypes,
                        symbolicState.concreteValues,
                        symbolicState.termMap
                    )
                )
                totalNewCandidates += newCands.size
                candidates += newCands.map { it to method }
            }

            prevVertex.linkDown(currentVertex)
            prevVertex = currentVertex
        }
        if (totalNewCandidates == 0) {
            val a= 10
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
