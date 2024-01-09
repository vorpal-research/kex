package org.vorpal.research.kex.asm.analysis.concolic.coverage

import info.leadinglight.jdot.enums.Color
import kotlinx.collections.immutable.PersistentList
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexRtManager.rtUnmapped
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CallOpcode
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.TerminateInst
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.Viewable
import org.vorpal.research.kthelper.tryOrNull


@Suppress("MemberVisibilityCanBePrivate")
class InstructionGraph(
    val targets: Set<Method>
) : Viewable {
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
            return minTargetDistance to minUncoveredDistance
        }
    }

    fun getVertex(instruction: Instruction): Vertex {
        if (instruction in nodes) {
            return nodes[instruction]!!
        } else {
            val method = instruction.parent.method
            ktassert(instruction == method.body.entry.first())

            val queue = queueOf<Triple<Vertex?, BasicBlock, Int>>()
            queue += Triple(null, method.body.entry, 0)
            queue.addAll(method.body.catchEntries.map { Triple(null, it, 0) })
            val visited = mutableSetOf<Pair<Instruction?, Instruction>>()
            val resolves = mutableMapOf<Instruction, List<Method>>()

            while (queue.isNotEmpty()) {
                val (prev, block, index) = queue.poll()
                val current = block.instructions[index]
                if (prev?.instruction to current in visited) continue
                visited += prev?.instruction to current

                val vertex = nodes.getOrPut(current) { Vertex(current) }
                prev?.linkDown(vertex)

                when (current) {
                    is CallInst -> {
                        val resolvedMethods = resolves.getOrPut(current) {
                            when (current.opcode) {
                                CallOpcode.STATIC -> listOf(current.method)
                                CallOpcode.SPECIAL -> listOf(current.method)
                                CallOpcode.INTERFACE, CallOpcode.VIRTUAL -> {
                                    val currentMethod = current.method

                                    val targetPackages = targets.map { it.klass.pkg }.toSet()

                                    val retTypeMapped = currentMethod.returnType.rtMapped
                                    val argTypesMapped = currentMethod.argTypes.mapToArray { it.rtMapped }
                                    val retTypeUnmapped = currentMethod.returnType.rtUnmapped
                                    val argTypesUnmapped = currentMethod.argTypes.mapToArray { it.rtUnmapped }
                                    instantiationManager.getAllConcreteSubtypes(
                                        currentMethod.klass,
                                        AccessModifier.Private
                                    )
                                        .filter { klass -> targetPackages.any { it.isParent(klass.pkg) } }
                                        .mapNotNullTo(mutableSetOf()) {
                                            tryOrNull {
                                                if (it.isKexRt) {
                                                    it.getMethod(currentMethod.name, retTypeMapped, *argTypesMapped)
                                                } else {
                                                    it.getMethod(currentMethod.name, retTypeUnmapped, *argTypesUnmapped)
                                                }
                                            }
                                        }
                                        .filter { it.hasBody }
                                }
                            }.filter { it.hasBody }
                        }
                        var connectedExits = false
                        for (candidate in resolvedMethods) {
                            queue += Triple(vertex, candidate.body.entry, 0)
                            queue.addAll(candidate.body.catchEntries.map { Triple(null, it, 0) })

                            candidate.body.flatten().filterIsInstance<ReturnInst>().forEach {
                                connectedExits = true
                                val returnVertex = nodes.getOrPut(it) { Vertex(it) }
                                queue += Triple(returnVertex, block, index + 1)
                            }
                        }
                        if (!connectedExits) {
                            queue += Triple(vertex, block, index + 1)
                        }
                    }

                    is TerminateInst -> current.successors.forEach {
                        queue += Triple(vertex, it, 0)
                    }

                    else -> queue += Triple(vertex, block, index + 1)
                }
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
