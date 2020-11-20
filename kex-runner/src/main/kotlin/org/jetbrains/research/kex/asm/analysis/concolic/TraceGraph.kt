package org.jetbrains.research.kex.asm.analysis.concolic

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.collection.stackOf
import org.jetbrains.research.kex.trace.`object`.*

open class TraceGraph(startTrace: Trace) {

    data class Vertex(val action: Action,
                      val predecessors: MutableCollection<Vertex>,
                      val successors: MutableCollection<Vertex>) {
        override fun hashCode(): Int {
            return action.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (other is Vertex)
                return this.action formalEquals other.action
            return false
        }

        override fun toString(): String {
            return action.toString()
        }
    }

    open class Branch(val actions: List<Action>) {
        fun context(len: Int) = Context(actions.takeLast(len))

        override fun equals(other: Any?): Boolean {
            if (other is Branch)
                return this.actions.zip(other.actions).map { (a, b) -> a formalEquals b }.all { it }
            return false
        }

        override fun hashCode(): Int {
            return actions.hashCode()
        }
    }

    class Context(actions: List<Action>) : Branch(actions)

    open fun toBranch(trace: Trace): Branch {
        return Branch(trace.actions)
    }

    val vertices: MutableCollection<Vertex> = mutableSetOf()
    val traces: MutableCollection<Trace> = mutableListOf()
    val rootToLeaf = mutableMapOf<Vertex, Vertex>()
    val leafToRoot
        get() = rootToLeaf.entries.associate { (k, v) -> v to k }
    var depth: Int = 0
        private set

    init {
        traces.add(startTrace)
        val actionTail = startTrace.actions
        val root = Vertex(actionTail[0], mutableSetOf(), mutableSetOf())
        vertices.add(root)
        for (action in actionTail.drop(1)) {
            val currPred = vertices.last()
            val currVertex = Vertex(action, mutableSetOf(currPred), mutableSetOf())
            currPred.successors.add(currVertex)
            vertices.add(currVertex)
        }
        rootToLeaf[root] = vertices.last()
        depth = actionTail.size
    }

    fun getTraces(): List<Trace> {
        return traces.toList()
    }

    fun getTraces(depth: Int): List<Trace> {
        return getTraces().filter { it.actions.size == depth }
    }

    fun getTracesAndBranches(): List<Pair<Trace, Branch>> {
        val traces = getTraces()
        val branches = traces.map { toBranch(it) }
        return traces.zip(branches)
    }

    open fun addTrace(trace: Trace) {
        traces.add(trace)
        val methodStack = stackOf<MethodEntry>()
        val foundVerticesStack = stackOf<MutableSet<Vertex>>()
        var previousVertex: Vertex? = null
        trace.actions.forEach { action ->
            if (action is MethodEntry) {
                methodStack.push(action)
                foundVerticesStack.push(mutableSetOf())
            }
            // TODO:? Check if predecessor in same method
            val found = action.findExcept(foundVerticesStack.peek()) ?: wrapAndAddAction(action, previousVertex)
            previousVertex?.successors?.add(found)
            found.predecessors.addAll(vertices.filter { found in it.successors })
            foundVerticesStack.peek().add(found)
            previousVertex = found

            if (action is MethodReturn && action.method == methodStack.peek().method) {
                methodStack.pop()
                foundVerticesStack.pop()
            }
        }
        rootToLeaf[trace.actions.first().find()!!] = trace.actions.last().find()!!
        depth = maxOf(depth, trace.actions.size)
    }

    private fun wrapAndAddAction(action: Action, predecessor: Vertex?): Vertex {
        val pred = listOfNotNull(predecessor).toMutableSet()
        val vert = Vertex(action, pred, mutableSetOf())
        vertices.add(vert)
        return vert
    }


    protected fun bfsApply(start: Vertex, func: (Vertex) -> Unit) {
        val queue = queueOf(start)
        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            func(curr)
            queue.addAll(curr.successors)
        }
    }

    protected fun bfsFullApply(func: (Vertex) -> Unit) {
        val visited = mutableSetOf<Vertex>()
        val queue = queueOf(rootToLeaf.keys)
        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            if (curr in visited)
                continue
            func(curr)
            visited.add(curr)
            queue.addAll(curr.successors)
        }
    }

    fun Action.find() = vertices.find { it.action formalEquals this }

    fun Action.findExcept(foundVertices: Set<Vertex>) = vertices.minus(foundVertices).find { it.action formalEquals this }

}

class DominatorTraceGraph(startTrace: Trace) : TraceGraph(startTrace) {
    private val dominatorMap = mutableMapOf<Vertex, Set<Vertex>>()
    private var nonDominatingVertices = setOf<Vertex>()

    init {
        update()
    }

    override fun toBranch(trace: Trace): Branch {
        return Branch(trace.actions.filter { act ->
            nonDominatingVertices.map { v -> v.action }
                    .any { v -> v formalEquals act }
        })
    }

    private fun update() {
        initRoots()
        updateDominatorMap()
        updateNonDominatingVertices()
    }

    private fun initRoots() {
        rootToLeaf.keys.forEach {
            dominatorMap[it] = setOf(it)
        }
    }

    private fun updateDominatorMap() {
        bfsFullApply {
            if (it.predecessors.isEmpty())
                updateDomMapRoots(it)
            else
                updateDomMapGeneral(it)
        }
    }

    private fun updateDomMapRoots(vertex: Vertex) {
        dominatorMap[vertex] = setOf(vertex)
    }

    private fun updateDomMapGeneral(vertex: Vertex) {
        dominatorMap[vertex] = vertex.predecessors
                .map { dominatorMap[it] ?: recoveryUpdateVertex(it) }
                .reduce { acc, set -> acc.intersect(set) }
                .union(setOf(vertex))
    }

    private fun recoveryUpdateVertex(vertex: Vertex): Set<Vertex> {
        updateDomMapGeneral(vertex)
        return dominatorMap[vertex]!!
    }

    private fun updateNonDominatingVertices() {
        nonDominatingVertices = vertices.filter { vert -> vert.dominatesOnlyItself() }.toSet()
    }

    private fun Vertex.dominatesOnlyItself(): Boolean {
        return dominatorMap.filterValues { doms -> this in doms }.size == 1
    }

    override fun addTrace(trace: Trace) {
        super.addTrace(trace)
        update()
    }
}