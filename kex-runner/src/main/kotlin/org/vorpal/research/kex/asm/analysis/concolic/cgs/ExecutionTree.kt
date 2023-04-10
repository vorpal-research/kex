package org.vorpal.research.kex.asm.analysis.concolic.cgs

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.receiver
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.trace.symbolic.Clause
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.PersistentPathCondition
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.graph.DominatorTree
import org.vorpal.research.kthelper.graph.DominatorTreeBuilder
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.PredecessorGraph
import org.vorpal.research.kthelper.graph.Viewable
import org.vorpal.research.kthelper.logging.log


sealed class Vertex(val clause: Clause) : PredecessorGraph.PredecessorVertex<Vertex> {
    private val upEdges = mutableSetOf<Vertex>()
    val downEdges = mutableSetOf<Vertex>()
    val states = mutableMapOf<PersistentPathCondition, PersistentSymbolicState>()

    override val predecessors: Set<Vertex>
        get() = upEdges

    override val successors: Set<Vertex>
        get() = downEdges

    operator fun set(path: PersistentPathCondition, state: PersistentSymbolicState) {
        states[path] = state
    }

    fun addUpEdge(vertex: Vertex) {
        upEdges += vertex
    }

    fun addDownEdge(vertex: Vertex) {
        downEdges += vertex
    }
}

class ClauseVertex(clause: Clause) : Vertex(clause) {
    override fun toString() = "${clause.predicate}"
}

class PathVertex(clause: PathClause) : Vertex(clause) {
    val pathClause = clause
    override fun toString() = "${clause.predicate}"
}

data class Context(
    val context: PersistentList<PathVertex>,
    val fullPath: PersistentPathCondition,
    val symbolicState: PersistentSymbolicState
) {
    @Suppress("unused")
    val condition get() = context.last()
    val size get() = context.size
}

private typealias Branch = Pair<Instruction, PathClauseType>

private val PathVertex.branch get() = Branch(pathClause.instruction, pathClause.type)

class ExecutionTree : PredecessorGraph<Vertex>, Viewable {
    private val _nodes = mutableMapOf<Clause, Vertex>()
    private var _root: Vertex? = null
    private var dominators: DominatorTree<Vertex>? = null
    private val edges = mutableMapOf<Clause, PathVertex>()
    private val exhaustedVertices = mutableSetOf<PathVertex>()
    private val exhaustiveness = mutableMapOf<Branch, MutableSet<PathVertex>>()
    private val hasEntry get() = _root != null

    override val entry get() = _root!!
    override val nodes: Set<Vertex>
        get() = _nodes.values.toSet()
    var depth: Int = 0
        private set

    fun isEmpty(): Boolean = _root == null

    fun getPathVertex(clause: Clause) = edges.getValue(clause)

    @Suppress("unused")
    fun isExhausted(clause: Clause) = isExhausted(getPathVertex(clause))
    fun isExhausted(vertex: PathVertex) = vertex in exhaustedVertices

    fun markExhausted(clause: Clause) {
        exhaustedVertices += getPathVertex(clause)
    }

    fun getBranches(depth: Int): Set<PathVertex> = getBranchDepths().filter { it.value == depth }.keys

    fun addTrace(symbolicState: PersistentSymbolicState) {
        var prevVertex: Vertex? = null

        var pathClauses = 1
        for (current in symbolicState.clauses) {
            val currentVertex = _nodes.getOrPut(current) {
                when (current) {
                    is PathClause -> PathVertex(current).also {
                        edges[current] = it
                    }

                    else -> ClauseVertex(current).also {
                        if (_root == null) {
                            _root = it
                        }
                    }
                }
            }
            if (currentVertex is PathVertex) {
                currentVertex[symbolicState.path.subPath(0, pathClauses++)] = symbolicState
            }

            prevVertex?.let { prev ->
                prev.addDownEdge(currentVertex)
                currentVertex.addUpEdge(prev)
            }

            if (currentVertex is PathVertex) {
                if (!isExhausted(currentVertex) && currentVertex.isInstructionExhaustive) {
                    exhaustedVertices += currentVertex
                }
                exhaustiveness
                    .getOrPut(currentVertex.branch, ::mutableSetOf)
                    .add(currentVertex)
            }

            prevVertex = currentVertex
        }

        val currentDepth = symbolicState.clauses.count { it.predicate.type is PredicateType.Path }
        if (currentDepth > depth)
            depth = currentDepth
        dominators = DominatorTreeBuilder(this).build()
    }

    private fun Vertex.dominates(other: Vertex) = dominators?.let { tree ->
        tree[this]?.dominates(other)
    } ?: false

    fun contexts(pathVertex: PathVertex, k: Int): List<Context> = pathVertex.states.map { (path, state) ->
        Context(
            path.builder()
                .map { edges[it]!! }
                .filter { !it.dominates(pathVertex) }
                .take(k)
                .toPersistentList(),
            path,
            state
        )
    }

    private fun getBranchDepths(): Map<PathVertex, Int> {
        if (!hasEntry) return emptyMap()

        val search = mutableMapOf<PathVertex, Int>()
        val visited = mutableSetOf<Vertex>()
        val queue = queueOf<Pair<Vertex, Int>>()
        queue.add(entry to 1)
        while (queue.isNotEmpty()) {
            val (top, depth) = queue.poll()
            if (top !in visited) {
                visited += top
                for (vertex in top.downEdges.filterIsInstance<PathVertex>()) {
                    search.merge(vertex, depth, ::minOf)
                }
                top.downEdges.filter { it !in visited }.forEach {
                    val newDepth = if (it is PathVertex) depth + 1 else depth
                    queue.add(it to newDepth)
                }
            }
        }
        return search
    }

    private val PathVertex.isInstructionExhaustive: Boolean
        get() {
            val pathClause = clause as PathClause

            val allNeighbours = exhaustiveness.getOrDefault(branch, mutableSetOf())
            val allPredicates = allNeighbours.map {
                it.clause.predicate
            }

            return when (pathClause.type) {
                PathClauseType.OVERLOAD_CHECK -> false
                PathClauseType.TYPE_CHECK -> allPredicates.map { it as EqualityPredicate }
                    .mapTo(mutableSetOf()) { it.rhv }.size == 2

                PathClauseType.NULL_CHECK -> allPredicates.map { it as EqualityPredicate }
                    .mapTo(mutableSetOf()) { it.rhv }.size == 2

                PathClauseType.BOUNDS_CHECK -> allPredicates.mapTo(mutableSetOf()) { it as EqualityPredicate }.size == 2
                PathClauseType.CONDITION_CHECK -> when (val inst = clause.instruction) {
                    is BranchInst -> allPredicates.mapTo(mutableSetOf()) { it as EqualityPredicate }.size == 2
                    is SwitchInst -> {
                        val visitedValues = allPredicates.mapTo(mutableSetOf()) {
                            when (it) {
                                is EqualityPredicate -> it.rhv
                                else -> it.receiver
                            }
                        }
                        val keys = inst.branches.keys.mapTo(mutableSetOf()) { term { value(it) } }
                        keys.all { it in visitedValues } && visitedValues.size >= keys.size + 1
                    }

                    is TableSwitchInst -> {
                        val visitedValues = allPredicates.mapTo(mutableSetOf()) {
                            when (it) {
                                is EqualityPredicate -> it.rhv
                                else -> it.receiver
                            }
                        }
                        val keys = inst.range.mapTo(mutableSetOf()) { term { const(it) } }
                        keys.all { it in visitedValues } && visitedValues.size >= keys.size + 1
                    }

                    else -> false.also {
                        log.error("Unknown instruction in condition check ${inst.print()}")
                    }
                }
            }
        }

    override val graphView: List<GraphView>
        get() {
            val graphNodes = mutableMapOf<Vertex, GraphView>()
            val depths = getBranchDepths()

            var i = 0
            for (vertex in nodes) {
                graphNodes[vertex] = GraphView("${i++}", "$vertex")
            }

            for (vertex in nodes) {
                val current = graphNodes.getValue(vertex)
                for (child in vertex.downEdges) {
                    val suffix = depths[child]?.let { "$it" } ?: ""
                    current.addSuccessor(graphNodes.getValue(child), suffix)
                }
            }

            return graphNodes.values.toList()
        }
}
