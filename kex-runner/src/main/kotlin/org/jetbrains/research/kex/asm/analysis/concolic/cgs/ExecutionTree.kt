package org.jetbrains.research.kex.asm.analysis.concolic.cgs

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.trace.symbolic.Clause
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kthelper.graph.GraphView
import org.jetbrains.research.kthelper.graph.PredecessorGraph
import org.jetbrains.research.kthelper.graph.Viewable

class Vertex(val clause: Clause) : PredecessorGraph.PredecessorVertex<Vertex> {
    val upEdges = mutableSetOf<Vertex>()
    val downEdges = mutableMapOf<Edge, Vertex>()
    val traces = mutableSetOf<SymbolicState>()

    override val predecessors: Set<Vertex>
        get() = upEdges

    override val successors: Set<Vertex>
        get() = downEdges.values.toSet()

    operator fun set(edge: Edge, vertex: Vertex) {
        downEdges[edge] = vertex
    }

    operator fun get(edge: Edge) = downEdges.getValue(edge)

    operator fun plusAssign(symbolicState: SymbolicState) {
        traces += symbolicState
    }

    operator fun plusAssign(vertex: Vertex) {
        upEdges += vertex
    }

    override fun toString() = "${clause.predicate}"
}

sealed class Edge
object StraightEdge : Edge() {
    override fun toString() = ""
}
data class PathEdge(val clause: Clause) : Edge() {
    override fun toString() = "${clause.predicate}"
}

class ExecutionTree : PredecessorGraph<Vertex>, Viewable {
    private val _nodes = mutableMapOf<Clause, Vertex>()
    private var _root: Vertex? = null

    override val entry get() = _root!!

    override val nodes: Set<Vertex>
        get() = _nodes.values.toSet()

    fun addTrace(symbolicState: SymbolicState) {
        var prevVertex: Vertex? = null
        var prevEdge: Edge = StraightEdge

        for (predicate in (symbolicState.state as BasicState)) {
            val current = Clause(symbolicState[predicate], predicate)
            when (predicate.type) {
                is PredicateType.Path -> prevEdge = PathEdge(current)
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
                    }
                    currentVertex += symbolicState

                    prevVertex = currentVertex
                    prevEdge = StraightEdge
                }
            }
        }
    }

    override val graphView: List<GraphView>
        get() {
            val graphNodes = mutableMapOf<Vertex, GraphView>()

            for (vertex in nodes) {
                graphNodes[vertex] = GraphView("$vertex", "$vertex")
            }

            for (vertex in nodes) {
                val current = graphNodes.getValue(vertex)
                for ((edge, child) in vertex.downEdges) {
                    current.addSuccessor(graphNodes.getValue(child), "$edge")
                }
            }

            return graphNodes.values.toList()
        }
}