package org.jetbrains.research.kex.asm.analysis.concolic.cgs

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.trace.symbolic.Clause
import org.jetbrains.research.kex.trace.symbolic.PathCondition
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.graph.*

class Vertex(val clause: Clause) : PredecessorGraph.PredecessorVertex<Vertex> {
    val upEdges = mutableSetOf<Vertex>()
    val downEdges = mutableMapOf<Edge, Vertex>()
    val states = mutableMapOf<PathCondition, SymbolicState>()

    override val predecessors: Set<Vertex>
        get() = upEdges

    override val successors: Set<Vertex>
        get() = downEdges.values.toSet()

    operator fun set(edge: Edge, vertex: Vertex) {
        downEdges[edge] = vertex
    }

    operator fun get(edge: Edge) = downEdges.getValue(edge)

    operator fun set(path: PathCondition, state: SymbolicState) {
        states[path] = state
    }

    operator fun plusAssign(vertex: Vertex) {
        upEdges += vertex
    }

    override fun toString() = "${clause.predicate}"
}

sealed class Edge {
    private val vertices = mutableListOf<Vertex?>(null, null)

    var entry: Vertex
        get() = vertices.first()!!
        set(value) {
            vertices[0] = value
        }
    var exit
        get() = vertices.last()!!
        set(value) {
            vertices[1] = value
        }
}

class StraightEdge : Edge() {
    override fun toString() = ""

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StraightEdge) return false
        return true
    }
}

data class PathEdge(val clause: Clause) : Edge() {
    val traces = mutableMapOf<PathCondition, SymbolicState>()

    operator fun set(path: PathCondition, state: SymbolicState) {
        traces[path] = state
    }

    override fun toString() = "${clause.predicate}"
}

data class Context(
    val context: List<PathEdge>,
    val fullPath: PathCondition,
    val symbolicState: SymbolicState
) {
    val condition get() = context.last()
    val size get() = context.size
}

class ExecutionTree : PredecessorGraph<Vertex>, Viewable {
    private val _nodes = mutableMapOf<Clause, Vertex>()
    private var _root: Vertex? = null
    private var dominators: DominatorTree<Vertex>? = null
    private val edges = mutableMapOf<Clause, PathEdge>()

    val hasEntry get() = _root != null

    override val entry get() = _root!!
    override val nodes: Set<Vertex>
        get() = _nodes.values.toSet()
    var depth: Int = 0
        private set

    fun getBranches(depth: Int): Set<PathEdge> = getBranchDepths().filter { it.value == depth }.keys

    fun addTrace(symbolicState: SymbolicState) {
        var prevVertex: Vertex? = null
        var prevEdge: Edge = StraightEdge()

        var currentDepth = 0
        for (predicate in (symbolicState.state as BasicState)) {
            val current = Clause(symbolicState[predicate], predicate)
            when (predicate.type) {
                is PredicateType.Path -> prevEdge = edges.getOrPut(current) {
                    PathEdge(current).also {
                        ++currentDepth
                        edges[current] = it
                    }
                }
                else -> {
                    val currentVertex = _nodes.getOrPut(current) {
                        Vertex(current).also {
                            if (_root == null) {
                                _root = it
                            }
                        }
                    }
                    prevVertex?.let { prev ->
                        prev[prevEdge] = currentVertex
                        currentVertex += prev
                        prevEdge.entry = prev
                        prevEdge.exit = currentVertex
                    }
                    if (prevEdge is PathEdge) {
                        prevEdge[symbolicState.path.subPath(prevEdge.clause)] = symbolicState
                    }

                    prevVertex = currentVertex
                    prevEdge = StraightEdge()
                }
            }
        }

        if (currentDepth > depth) depth = currentDepth
        dominators = DominatorTreeBuilder(this).build()
    }

    fun Vertex.dominates(other: Vertex) = dominators?.let { tree ->
        tree[this]?.dominates(other)
    } ?: false

    fun PathEdge.dominates(other: PathEdge) = this.entry.dominates(other.exit)

    fun contexts(pathEdge: PathEdge, k: Int): List<Context> = pathEdge.traces.map { (path, state) ->
        Context(path.reversed().map { edges[it]!! }.filter { !it.dominates(pathEdge) }.take(k), path, state)
    }

    private fun getBranchDepths(): Map<PathEdge, Int> {
        if (!hasEntry) return emptyMap()

        val search = mutableMapOf<PathEdge, Int>()
        val visited = mutableSetOf<Vertex>()
        val queue = queueOf<Pair<Vertex, Int>>()
        queue.add(entry to 1)
        while (queue.isNotEmpty()) {
            val (top, depth) = queue.poll()
            if (top !in visited) {
                visited += top
                for (edge in top.downEdges.keys.filterIsInstance<PathEdge>()) {
                    search.merge(edge, depth, ::minOf)
                }
                top.downEdges.filter { it.value !in visited }.forEach {
                    val newDepth = if (it.key is PathEdge) depth + 1 else depth
                    queue.add(it.value to newDepth)
                }
            }
        }
        return search
    }

    override val graphView: List<GraphView>
        get() {
            val graphNodes = mutableMapOf<Vertex, GraphView>()
            val depths = getBranchDepths()

            for (vertex in nodes) {
                graphNodes[vertex] = GraphView("$vertex", "$vertex")
            }

            for (vertex in nodes) {
                val current = graphNodes.getValue(vertex)
                for ((edge, child) in vertex.downEdges) {
                    val suffix = depths[edge]?.let { " - $it" } ?: ""
                    current.addSuccessor(graphNodes.getValue(child), "$edge$suffix")
                }
            }

            return graphNodes.values.toList()
        }
}