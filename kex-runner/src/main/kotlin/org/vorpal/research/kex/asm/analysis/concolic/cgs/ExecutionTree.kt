package org.vorpal.research.kex.asm.analysis.concolic.cgs

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.trace.symbolic.Clause
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.PersistentPathCondition
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.StateClause
import org.vorpal.research.kfg.ir.value.EmptyUsageContext
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.graph.DominatorTree
import org.vorpal.research.kthelper.graph.DominatorTreeBuilder
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.PredecessorGraph
import org.vorpal.research.kthelper.graph.Viewable

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Context

        return context == other.context
    }

    override fun hashCode(): Int {
        return context.hashCode()
    }
}

private typealias Branch = Pair<Instruction, PathClauseType>

private val PathVertex.branch get() = Branch(pathClause.instruction, pathClause.type)

class ExecutionTree(val ctx: ExecutionContext) : PredecessorGraph<Vertex>, Viewable {
    private val root: Vertex = ClauseVertex(
        StateClause(
            ctx.cm.instruction.getUnreachable(EmptyUsageContext),
            state { const(true) equality true })
    )
    private val _nodes = mutableMapOf(root.clause to root)
    private var dominators: DominatorTree<Vertex>? = null
    private val edges = mutableMapOf<Clause, PathVertex>()
    private val exhaustiveness = mutableMapOf<Branch, MutableSet<PathVertex>>()

    override val entry get() = root
    override val nodes: Set<Vertex>
        get() = _nodes.values.toSet()
    var depth: Int = 0
        private set

    fun isEmpty(): Boolean = root.downEdges.isEmpty()

    fun getPathVertex(clause: Clause) = edges.getValue(clause)

    @Suppress("unused")
    fun isExhausted(clause: Clause) = isExhausted(getPathVertex(clause))
    fun isExhausted(vertex: PathVertex): Boolean {
        val branch = vertex.branch
        val branches = exhaustiveness.getOrDefault(branch, emptySet())
        return branches.size == when (branch.second) {
            PathClauseType.NULL_CHECK -> 2
            PathClauseType.TYPE_CHECK -> 2
            PathClauseType.OVERLOAD_CHECK -> instantiationManager.getAllConcreteSubtypes(
                ((vertex.pathClause.instruction as CallInst).callee.type as ClassType).klass, ctx.accessLevel
            ).size

            PathClauseType.CONDITION_CHECK -> when (val inst = branch.first) {
                is SwitchInst -> inst.branches.size + 1
                is TableSwitchInst -> inst.branches.size + 1
                else -> 2
            }

            PathClauseType.BOUNDS_CHECK -> 2
        }
    }

    fun getBranches(depth: Int): Set<PathVertex> = getBranchDepths().filter { it.value == depth }.keys

    fun addTrace(symbolicState: PersistentSymbolicState) {
        var prevVertex = root
        var pathClauses = 1
        for (current in symbolicState.clauses) {
            val currentVertex = _nodes.getOrPut(current) {
                when (current) {
                    is PathClause -> PathVertex(current).also {
                        edges[current] = it
                    }

                    else -> ClauseVertex(current)
                }
            }
            if (currentVertex is PathVertex) {
                currentVertex[symbolicState.path.subPath(0, pathClauses++)] = symbolicState
            }

            prevVertex.let { prev ->
                prev.addDownEdge(currentVertex)
                currentVertex.addUpEdge(prev)
            }

            if (currentVertex is PathVertex) {
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

    fun contexts(pathVertex: PathVertex, k: Int): Set<Context> = pathVertex.states.mapTo(mutableSetOf()) { (path, state) ->
        Context(
            path.builder()
                .map { edges[it]!! }
                .filter { !it.dominates(pathVertex) }
                .takeLast(k)
                .toPersistentList(),
            path,
            state
        )
    }

    private fun getBranchDepths(): Map<PathVertex, Int> {
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

    override val graphView: List<GraphView>
        get() {
            val graphNodes = mutableMapOf<Vertex, GraphView>()
            val depths = getBranchDepths()

            var i = 0
            for (vertex in nodes) {
                graphNodes[vertex] = GraphView("${i++}", "$vertex") {
                    it.setColor(
                        when {
                            vertex is PathVertex && isExhausted(vertex) -> {
                                info.leadinglight.jdot.enums.Color.X11.green
                            }

                            vertex is PathVertex -> {
                                info.leadinglight.jdot.enums.Color.X11.red
                            }

                            else -> {
                                info.leadinglight.jdot.enums.Color.X11.blue
                            }
                        }
                    )
                }
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
