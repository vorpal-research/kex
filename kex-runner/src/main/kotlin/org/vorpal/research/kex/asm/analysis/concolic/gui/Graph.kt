package org.vorpal.research.kex.asm.analysis.concolic.gui

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.trace.symbolic.Clause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState

class Graph {

    private var vertexId = 0
    private val nodes = mutableMapOf<Clause, Vertex>()

    val depth: Int
        get() = vertexId
    val vertices: List<Vertex>
        get() = nodes.values.toList()

    fun addTrace(symbolicState: PersistentSymbolicState): List<Vertex> {
        val vertices = mutableListOf<Vertex>()
        for (clause in symbolicState.clauses) {
            val vertex = nodes.getOrPut(clause) { Vertex(vertexId++, clause.predicate.toString()) }
            vertices.add(vertex)
        }
        return vertices
    }

    override fun toString(): String {
        return "Graph(depth=$depth):\n\t${vertices.joinToString("\n\t")})"
    }
}

@Serializable
data class Vertex(
    val id: Int,
    val name: String
) {
    override fun toString() = "Vertex(id=$id, name='$name')"
}