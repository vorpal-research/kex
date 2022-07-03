package org.vorpal.research.kex.asm.analysis.concolic.cgs

import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.receiver
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.trace.symbolic.Clause
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PathClauseType
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.graph.*
import org.vorpal.research.kthelper.logging.log


sealed class Vertex(val clause: Clause) : PredecessorGraph.PredecessorVertex<Vertex> {
    val upEdges = mutableSetOf<Vertex>()
    val downEdges = mutableSetOf<Vertex>()
    val states = mutableMapOf<List<PathClause>, SymbolicState>()

    override val predecessors: Set<Vertex>
        get() = upEdges

    override val successors: Set<Vertex>
        get() = downEdges

    operator fun set(path: List<PathClause>, state: SymbolicState) {
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
    val context: List<PathVertex>,
    val fullPath: List<PathClause>,
    val symbolicState: SymbolicState
) {
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

    fun isExhausted(clause: Clause) = isExhausted(getPathVertex(clause))
    fun isExhausted(vertex: PathVertex) = vertex in exhaustedVertices

    fun markExhausted(clause: Clause) {
        exhaustedVertices += getPathVertex(clause)
    }

    fun getBranches(depth: Int): Set<PathVertex> = getBranchDepths().filter { it.value == depth }.keys

    fun addTrace(symbolicState: SymbolicState) {
        var prevVertex: Vertex? = null

        for (current in symbolicState.clauses) {
            val currentVertex = _nodes.getOrPut(current) {
                when (current) {
                    is PathClause -> PathVertex(current).also {
                        it[symbolicState.subPath(current)] = symbolicState
                        edges[current] = it
                    }
                    else -> ClauseVertex(current).also {
                        if (_root == null) {
                            _root = it
                        }
                    }
                }
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

    fun Vertex.dominates(other: Vertex) = dominators?.let { tree ->
        tree[this]?.dominates(other)
    } ?: false

    fun contexts(pathVertex: PathVertex, k: Int): List<Context> = pathVertex.states.map { (path, state) ->
        Context(
            path.reversed()
                .map { edges[it]!! }
                .filter { !it.dominates(pathVertex) }
                .take(k),
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

//    private val PathVertex.isExhaustive: Boolean
//        get() {
//            val neighbors = this.predecessors.flatMap { it.successors }
//
//            return when (val inst = clause.instruction) {
//                is BranchInst -> neighbors.size == 2
//                is SwitchInst -> neighbors.size == (inst.branches.size + 1)
//                is TableSwitchInst -> neighbors.size == (inst.range.length + 1)
//                else -> when (val pred = clause.predicate) {
//                    is EqualityPredicate -> when (pred.lhv) {
//                        is InstanceOfTerm -> false
//                        else -> neighbors.size == 2
//                    }
//                    else -> false
//                }
//            }
//        }

    private val PathVertex.isInstructionExhaustive: Boolean
        get() {
            val pathClause = clause as PathClause

            val allNeighbours = exhaustiveness.getOrDefault(branch, mutableSetOf())
            val allPredicates = allNeighbours.map {
                it.clause.predicate
            }

            return when (pathClause.type) {
                PathClauseType.TYPE_CHECK -> false
                PathClauseType.NULL_CHECK -> allPredicates.map { it as EqualityPredicate }.map { it.rhv }
                    .toSet().size == 2
                PathClauseType.BOUNDS_CHECK -> allPredicates.map { it as EqualityPredicate }.toSet().size == 2
                PathClauseType.CONDITION_CHECK -> when (val inst = clause.instruction) {
                    is BranchInst -> allPredicates.map { it as EqualityPredicate }.toSet().size == 2
                    is SwitchInst -> {
                        val visitedValues = allPredicates
                            .map {
                                when (it) {
                                    is EqualityPredicate -> it.rhv
                                    else -> it.receiver
                                }
                            }
                            .toSet()
                        val keys = inst.branches.keys.map { term { value(it) } }.toSet()
                        keys.all { it in visitedValues } && visitedValues.size >= keys.size + 1
                    }
                    is TableSwitchInst -> {
                        val visitedValues = allPredicates
                            .map {
                                when (it) {
                                    is EqualityPredicate -> it.rhv
                                    else -> it.receiver
                                }
                            }
                            .toSet()
                        val keys = inst.range.map { term { const(it) } }.toSet()
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
