package org.vorpal.research.kex.asm.analysis.concolic.gui

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.trace.symbolic.Clause
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState

class Graph {

    private var vertexId = 0

    private val nodes = mutableMapOf<Clause, Vertex>()
    private val states = mutableMapOf<PathClause, PersistentSymbolicState>()

    val depth: Int
        get() = vertexId

    @Suppress("MemberVisibilityCanBePrivate")
    val vertices: List<Vertex>
        get() = nodes.values.toList()

    fun addTrace(symbolicState: PersistentSymbolicState): List<Vertex> {
        val vertices = mutableListOf<Vertex>()
        for (clause in symbolicState.clauses) {
            if (clause is PathClause) states[clause] = symbolicState

            val vertex = nodes.getOrPut(clause) { Vertex(vertexId++, clause.predicate.toString()) }
            vertices.add(vertex)
        }
        return vertices
    }

    fun findClauseByVertex(vertex: Vertex): Clause? {
        return nodes.firstNotNullOfOrNull { if (it.value == vertex) it.key else null }
    }

    fun findStateByPathClause(pathClause: PathClause): PersistentSymbolicState? = states[pathClause]

    override fun toString(): String {
        return "Graph(depth=$depth):\n\t${vertices.joinToString("\n\t")})"
    }
}

@Serializable
data class Vertex(
    val id: Int,
    val name: String
)
