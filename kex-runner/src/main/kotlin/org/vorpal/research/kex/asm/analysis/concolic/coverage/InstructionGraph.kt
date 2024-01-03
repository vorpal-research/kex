package org.vorpal.research.kex.asm.analysis.concolic.coverage

import info.leadinglight.jdot.enums.Color
import kotlinx.collections.immutable.PersistentList
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.Viewable


class InstructionGraph : Viewable {
    private val nodes = mutableMapOf<Instruction, Vertex>()

    var covered = 0

    inner class Vertex(val instruction: Instruction) {
        var covered = false
        val upEdges = mutableSetOf<Vertex>()
        val downEdges = mutableSetOf<Vertex>()

        override fun toString(): String = instruction.print()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Vertex

            return instruction == other.instruction
        }

        override fun hashCode(): Int {
            return instruction.hashCode()
        }

        fun linkDown(other: Vertex) {
            downEdges += other
            other.upEdges += this
        }

        fun distanceToUncovered(
            targets: Set<Method>,
            initialStackTrace: PersistentList<Pair<Instruction?, Method>>
        ): Pair<Int, Int> {
            var minTargetDistance = Int.MAX_VALUE
            var minUncoveredDistance = Int.MAX_VALUE

            val distances = mutableMapOf<Vertex, Int>()
            distances[this] = 0
            val queue = queueOf(this to initialStackTrace)
            while (queue.isNotEmpty()) {
                val (current, stackTrace) = queue.poll()
                val currentDistance = distances[current]!!

                if (!current.covered) {
                    if (currentDistance < minUncoveredDistance) {
                        minUncoveredDistance = currentDistance
                    }
                    if (current.instruction.parent.method in targets && currentDistance < minTargetDistance) {
                        minTargetDistance = currentDistance
                    }
                    continue
                }

                val edges = when (current.instruction) {
                    is ReturnInst -> {
                        val newStackTrace = stackTrace.removeAt(stackTrace.size - 1)
                        if (newStackTrace.isEmpty()) continue
                        current.downEdges.filter { it.instruction.parent.method == newStackTrace.last().second }
                            .map { it to newStackTrace }
                    }

                    is CallInst -> {
                        current.downEdges.map {
                            if (it.instruction.parent.method != current.instruction.parent.method) {
                                it to stackTrace.add(it.instruction to it.instruction.parent.method)
                            } else {
                                it to stackTrace
                            }
                        }
                    }

                    else -> current.downEdges.map { it to stackTrace }
                }

                for ((edge, edgeStackTrace) in edges) {
                    val distance = distances.getOrDefault(edge, Int.MAX_VALUE)
                    val stepValue = when (current.instruction) {
                        is CallInst -> 2
                        is FieldLoadInst, is FieldStoreInst -> 1
                        is ArrayLoadInst, is ArrayStoreInst -> 1
                        is BranchInst -> 1
                        else -> 0
                    }
                    if (distance > (currentDistance + stepValue)) {
                        distances[edge] = currentDistance + stepValue
                        queue += edge to edgeStackTrace
                    }
                }
            }
//            object : Viewable {
//                override val graphView: List<GraphView>
//                    get() {
//                        val graphNodes = mutableMapOf<Vertex, GraphView>()
//
//                        var i = 0
//                        for (vertex in nodes.values) {
//                            graphNodes[vertex] = GraphView("${i++}", "$vertex".replace("\"", "\\\"")) {
//                                val color = when {
//                                    vertex == this@Vertex -> Color.X11.blue
//                                    vertex.instruction.parent.method in targets -> when {
//                                        vertex.covered -> Color.X11.green
//                                        else -> Color.X11.red
//                                    }
//                                    else -> Color.X11.black
//                                }
//                                it.setColor(color)
//                            }
//                        }
//
//                        for (vertex in nodes.values) {
//                            val current = graphNodes.getValue(vertex)
//                            for (child in vertex.downEdges) {
//                                current.addSuccessor(graphNodes.getValue(child)) {
//                                    it.setLabel(distances[child]?.toString() ?: "")
//                                }
//                            }
//                        }
//
//                        return graphNodes.values.toList()
//                    }
//            }.view()
            return minTargetDistance to minUncoveredDistance
        }
    }

    fun getVertex(instruction: Instruction): Vertex {
        if (instruction in nodes) {
            return nodes[instruction]!!
        } else {
            val method = instruction.parent.method
            ktassert(instruction == method.body.entry.first())

            val queue = queueOf<Pair<Vertex?, BasicBlock>>()
            queue += null to method.body.entry
            queue.addAll(method.body.catchEntries.map { null to it })
            val visited = mutableSetOf<Pair<Instruction?, BasicBlock>>()
            while (queue.isNotEmpty()) {
                var (prev, block) = queue.poll()
                if (prev?.instruction to block in visited) continue
                visited += prev?.instruction to block

                for (inst in block) {
                    val vertex = nodes.getOrPut(inst) { Vertex(inst) }
                    prev?.linkDown(vertex)
                    prev = vertex
                }

                queue.addAll(block.successors.map { prev to it })
            }
            return nodes.getOrPut(instruction) { Vertex(instruction) }
        }
    }

    fun addTrace(trace: List<Instruction>) {
        var prev: Vertex? = null
        for (inst in trace) {
            val current = getVertex(inst)
            if (!current.covered) ++covered

            current.covered = true
            prev?.linkDown(current)
            prev = current
        }
    }

    override val graphView: List<GraphView>
        get() {
            val graphNodes = mutableMapOf<Vertex, GraphView>()

            var i = 0
            for (vertex in nodes.values) {
                graphNodes[vertex] = GraphView("${i++}", "$vertex".replace("\"", "\\\"")) {
                    val color = when {
                        vertex.covered -> Color.X11.green
                        else -> Color.X11.red
                    }
                    it.setColor(color)
                }
            }

            for (vertex in nodes.values) {
                val current = graphNodes.getValue(vertex)
                for (child in vertex.downEdges) {
                    current.addSuccessor(graphNodes.getValue(child))
                }
            }

            return graphNodes.values.toList()
        }
}
