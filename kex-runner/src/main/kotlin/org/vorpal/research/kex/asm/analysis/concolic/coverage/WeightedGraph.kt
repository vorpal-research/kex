package org.vorpal.research.kex.asm.analysis.concolic.coverage

import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexRtManager.rtUnmapped
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import kotlin.math.max

class WeightedGraph(
    val targets: Set<Method>,
    val targetInstructions: Set<Instruction>
) {
    private val MIN_COVERED_SCORE = 40

    val nodes = mutableMapOf<Instruction, Vertex>()
    var unreachables: MutableList<List<Vertex>> = mutableListOf()

    inner class Vertex(val instruction: Instruction, val predecessors: MutableSet<Vertex>, var coveredScore: Int = 1) {

        val CYCLE_EDGE_SCORE = 4

        val upEdges = mutableSetOf<Vertex>()
        val downEdges = mutableSetOf<Vertex>()
        val cycleEdges = mutableMapOf<Vertex, Int>()
        var score = 0
        var isValid = false

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
            other.addPredecessors(predecessors + this)
//            other.predecessors += this
//            other.predecessors += predecessors
        }

        fun addPredecessors(pred: Set<Vertex>) {
            predecessors += pred
            for (v in downEdges) {
                v.addPredecessors(predecessors)
            }
        }

        fun addCycleEdge(other: Vertex) {
            cycleEdges[other] = CYCLE_EDGE_SCORE
            predecessors += other.predecessors
        }

        fun decreaseCycleEdgeWeight(other: Vertex) {
            val currentWeight = cycleEdges[other]
            if (currentWeight != null && currentWeight >= 1) {
                cycleEdges[other] = currentWeight - 1
            }
        }

        fun invalidate() {
            isValid = false
            predecessors.forEach { it.isValid = false }
        }

        fun recomputeScore(visited: MutableList<Vertex> = mutableListOf())  {
            if (isValid) return
            visited.add(this)
            if (visited.size > 100) {
                log.debug("Hahaha")
            }
            score = 0
            if (instruction in targetInstructions) {
                score += coveredScore
            }
            for (vertex in downEdges) {
                if (!vertex.isValid) {
                    vertex.recomputeScore(visited)
                }
                if (visited + vertex !in unreachables) {
                    score += vertex.score
                }
            }
            for (s in cycleEdges.values) {
                score += s
            }
            isValid = true
            visited.removeLast()
        }
    }

    data class QueueEntry(val prev: Vertex?, val block: BasicBlock, val index: Int)

    fun getVertex(instruction: Instruction): Vertex {
        if (instruction in nodes) {
            return nodes[instruction]!!
        } else {
            val method = instruction.parent.method
            ktassert(instruction == method.body.entry.first())

            val queue = queueOf<QueueEntry>()
            queue += QueueEntry(null, method.body.entry, 0)
            queue.addAll(method.body.catchEntries.map { QueueEntry(null, it, 0) })
            val visited = mutableSetOf<Pair<Instruction?, Instruction>>()
            val resolves = mutableMapOf<Instruction, List<Method>>()

            while (queue.isNotEmpty()) {
                val (prev, block, index) = queue.poll()
                val current = block.instructions[index]
                if (prev?.instruction to current in visited) continue
                visited += prev?.instruction to current

                val vertex = nodes.getOrPut(current) { Vertex(current, prev?.predecessors?.toMutableSet() ?: mutableSetOf()) }

                if (prev?.predecessors?.contains(vertex) == true) {
                    prev.addCycleEdge(vertex)
                }
                else {
                    prev?.linkDown(vertex)
                }

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
                            queue += QueueEntry(vertex, candidate.body.entry, 0)
                            queue.addAll(candidate.body.catchEntries.map { QueueEntry(null, it, 0) })

                            candidate.body.flatten().filterIsInstance<ReturnInst>().forEach {
                                connectedExits = true
                                val returnVertex = nodes.getOrPut(it) { Vertex(it, mutableSetOf()) }
                                queue += QueueEntry(returnVertex, block, index + 1)
                            }
                        }

                        if (!connectedExits) {
                            queue += QueueEntry(vertex, block, index + 1)
                        }
                    }

                    is TerminateInst -> current.successors.forEach {
                        queue += QueueEntry(vertex, it, 0)
                    }

                    else -> queue += QueueEntry(vertex, block, index + 1)
                }
            }

            return nodes.getOrPut(instruction) { Vertex(instruction, mutableSetOf()) }
        }
    }

    fun addTrace(trace: List<Instruction>) {
        var prev: Vertex? = null
        for (inst in trace) {
            val current = getVertex(inst)
            current.coveredScore = max(0, current.coveredScore-1)
            if (prev == null) {
                prev = current
                continue
            }
            if (prev.cycleEdges.contains(current)) {
                prev.decreaseCycleEdgeWeight(current)
                prev.invalidate()
            }
            else if (prev.predecessors.contains(current)) {
                prev.addCycleEdge(current)
            }
            else if (!prev.predecessors.contains(current)) {
                prev.linkDown(current)
            }

            prev = current
        }
        prev?.invalidate()
        targets.forEach { getVertex(it.body.entry.instructions.first()).recomputeScore() }
//        nodes[trace[0]]?.recomputeScore()
    }

    fun getPath(root: Instruction): List<Vertex> {
        var prev = getVertex(root)
//        if (!prev.isValid) {
//            prev.recomputeScore()
//        }
        val path = mutableListOf(prev)
        var coveredScore = 0
        while (prev.downEdges.size > 0 && coveredScore < MIN_COVERED_SCORE) {
            var nextVertex = prev.downEdges.maxBy { if (path + it in unreachables) 0 else it.score }
            val nextVertexInclCycles = prev.cycleEdges.maxByOrNull {  if (path + it in unreachables) 0 else it.value }
            if (nextVertexInclCycles != null && nextVertexInclCycles.value != 0) {
                // explore cycle
                val randomNum = (0..10).random()
                if (randomNum <= nextVertexInclCycles.value && nextVertex.score <= nextVertexInclCycles.key.score) {
                    nextVertex = nextVertexInclCycles.key
                    coveredScore += 1
                    // TODO: move to add trace
                    prev.decreaseCycleEdgeWeight(nextVertex)
                    prev.invalidate()
                }
            }

            nextVertex.coveredScore = max(0, nextVertex.coveredScore-1)

            if (nextVertex.score == 0) break

            if (nextVertex.instruction in targetInstructions) {
                // TODO: move to add trace
                coveredScore += nextVertex.coveredScore
            }
//            nextVertex.cycleEdges.values.forEach {
//                coveredScore += it
//            }
            path.add(nextVertex)
            prev = nextVertex
        }
        prev.invalidate()
        targets.forEach { getVertex(it.body.entry.instructions.first()).recomputeScore() }
        return path
    }


}