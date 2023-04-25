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

    fun addTrace(symbolicState: PersistentSymbolicState) {
        var prevVertex: Vertex? = null
        for (clause in symbolicState.clauses) {
            val currentVertex = nodes.getOrPut(clause) { Vertex(vertexId++, clause.predicate.toString()) }
            prevVertex?.successors?.add(currentVertex)
            prevVertex = currentVertex
        }
    }

    override fun toString() = vertices.joinToString("\n")
}

@Serializable
data class Vertex(
    val id: Int,
    val name: String,
    val successors: MutableSet<Vertex> = mutableSetOf()
) {
    override fun toString(): String {
        return "Vertex(id=$id, name='$name', successors=${successors.map { it.toStringSingle() }})"
    }

    fun toStringSingle() = "Vertex(id=$id, name='$name')"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vertex

        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }
}