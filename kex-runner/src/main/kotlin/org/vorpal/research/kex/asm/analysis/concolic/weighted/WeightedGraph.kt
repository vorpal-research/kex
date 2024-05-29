package org.vorpal.research.kex.asm.analysis.concolic.weighted

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexRtManager.rtUnmapped
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import kotlin.math.max

class WeightedGraph(
    val ctx: ExecutionContext,
    val targets: Set<Method>,
    val targetInstructions: Set<Instruction>
) {
    private val MIN_COVERED_SCORE = 40
    private val MAX_DEPTH = 3
    // nodes size greater than this number will cause MAX_DEPTH = 1 behavior
    private val MAX_NODES_SIZE = 1000
    val ISUFFICIENT_PATH_SCORE = 0.1

    private val nodes = mutableMapOf<Instruction, Vertex>()

    inner class Vertex(val instruction: Instruction, val predecessors: MutableSet<Vertex>, var coveredScore: Int = 1) {

        private val CYCLE_EDGE_SCORE = 4
        private val upEdges = mutableSetOf<Vertex>()
        private val downEdges = mutableSetOf<Vertex>()
        private val _cycleEdgesScores = mutableMapOf<Vertex, Int>()
        private val beforePathFindingCycleEdgesScores = mutableMapOf<Vertex, Int>()
        private var beforePathFindingScore = 0
        private var isValid = false

        val cycleEdgesScores: Map<Vertex, Int>
            get() = _cycleEdgesScores
        var score: Double = 0.0
            private set


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

        fun hasSuccessors() = downEdges.isNotEmpty() || _cycleEdgesScores.isNotEmpty()

        fun linkDown(other: Vertex) {
            downEdges += other
            other.upEdges += this
            other.addPredecessors(predecessors + this)
            invalidate()
        }

        // this function used only in case if we need to expand our graph after call instruction
        // so predecessors of other still contain this vertex and other predecessors
        fun breakLink(other: Vertex) {
            downEdges.remove(other)
        }

        fun addCycleEdge(other: Vertex) {
            _cycleEdgesScores[other] = CYCLE_EDGE_SCORE
            addPredecessors(other.predecessors)
            invalidate()
        }

        private fun addPredecessors(parentPredecessors: Set<Vertex>) {
            predecessors += parentPredecessors
            for (v in downEdges) {
                v.addPredecessors(predecessors)
            }
        }

        fun decreaseCycleEdgeWeight(other: Vertex) {
            val currentWeight = _cycleEdgesScores[other]
            if (currentWeight != null && currentWeight >= 1) {
                _cycleEdgesScores[other] = currentWeight - 1
            }
            invalidate()
        }

        fun reassignCycleEdgesScore(score: Int) {
            _cycleEdgesScores.forEach { (k, _) ->
                _cycleEdgesScores[k] = score
            }
        }

        fun decreaseCoveredScore() {
            coveredScore = max(0, coveredScore - 1)
            invalidate()
        }

        fun invalidate() {
            isValid = false
            predecessors.forEach { it.isValid = false }
        }

        fun saveScore() {
            beforePathFindingScore = coveredScore
            beforePathFindingCycleEdgesScores.clear()
            _cycleEdgesScores.forEach { (k, v) ->
                beforePathFindingCycleEdgesScores[k] = v
            }
        }

        fun restoreScore() {
            coveredScore = beforePathFindingScore
            beforePathFindingCycleEdgesScores.forEach { (k, v) ->
                _cycleEdgesScores[k] = v
            }
        }

        fun recomputeScore(currentMultiplierGraphVertex: MultiplierGraphVertex? = rootMultiplier.downEdges[this]) {
            if (isValid) return
            score = 0.0
            // if instruction not in target instruction, we are not interested in this coverage
            if (instruction in targetInstructions) {
                score += coveredScore
            }
            // recompute score for all successors
            for (vertex in downEdges) {
                val multiplierPathScore = currentMultiplierGraphVertex?.downEdges?.get(vertex)?.scoreMultiplier ?: 1.0
                // path is unreachable or was tried too many times
                if (multiplierPathScore <= ISUFFICIENT_PATH_SCORE) {
                    continue
                }
                vertex.recomputeScore(currentMultiplierGraphVertex?.downEdges?.get(vertex))
                score += vertex.score * multiplierPathScore
            }
            // add all cycle edges scores
            for (s in _cycleEdgesScores.values) {
                score += s
            }
            isValid = true
        }

        fun nextVertex(currentMultiplierGraphVertex: MultiplierGraphVertex?): Vertex? {
            if (downEdges.isEmpty() && _cycleEdgesScores.isEmpty()) return null

            val cycleEdgesScoresWithMultiplier = _cycleEdgesScores.mapValues {
                val scoreMultiplier = currentMultiplierGraphVertex?.downEdges?.get(it.key)?.scoreMultiplier ?: 1.0
                if (scoreMultiplier < ISUFFICIENT_PATH_SCORE) 0.0 else it.value * scoreMultiplier
            }
            val totalCycleEdgesScore = cycleEdgesScoresWithMultiplier.values.sum()
            // if a cycle is available, when explore it
            if (totalCycleEdgesScore > 0.1) {
                val random = ctx.random.nextDouble(totalCycleEdgesScore)
                var current = 0.0
                for (cycleEdge in cycleEdgesScoresWithMultiplier) {
                    current += cycleEdge.value
                    if (random < current) {
                        val nextVertex = cycleEdge.key
                        decreaseCycleEdgeWeight(nextVertex)
                        // jump to some previous vertexes, state computation is needed
                        invalidate()
                        recomputeScores()
                        return nextVertex
                    }
                }
            }
            val downEdgesScoresWithMultiplier = downEdges.associateWith {
                val scoreMultiplier = currentMultiplierGraphVertex?.downEdges?.get(it)?.scoreMultiplier ?: 1.0
                if (scoreMultiplier < ISUFFICIENT_PATH_SCORE) 0.0 else it.score * scoreMultiplier
            }
            val totalDownEdgesScore = downEdgesScoresWithMultiplier.values.sum()
            if (totalDownEdgesScore < 0.1) return null

            val random = ctx.random.nextDouble(totalDownEdgesScore)
            var current = 0.0
            for (edge in downEdgesScoresWithMultiplier) {
                current += edge.value
                if (random < current) {
                    return edge.key
                }
            }

            // everything is unreachable
            return null
        }
    }

    fun recomputeScores() = targets.forEach { getVertex(it.body.flatten().first()).restoreScore() }

    fun reassignCyclesEdgesScores(score: Int) {
        nodes.forEach { (_, v) ->
            v.reassignCycleEdgesScore(score)
        }
    }

    data class QueueEntry(val prev: Vertex?, val block: BasicBlock, val index: Int, val depth: Int)

    // previous is not null only in case if previous is a call instruction
    fun getVertex(instruction: Instruction, previous: Vertex? = null): Vertex {
        if (instruction in nodes) {
            return nodes[instruction]!!
        } else {
            val method = instruction.parent.method
            //ktassert(instruction == method.body.entry.first() || method.body.catchEntries.any { instruction == it.first() })

            (previous?.instruction as? CallInst)?.method?.body?.flatten()?.filterIsInstance<ReturnInst>()?.forEach {
                previous.breakLink(getVertex(it))
            }

            val queue = queueOf<QueueEntry>()
            queue.add(QueueEntry(previous, method.body.entry, 0, 0))
            queue.addAll(method.body.catchEntries.map { QueueEntry(null, it, 0, 0) })
            val visited = mutableSetOf<Pair<Instruction?, Instruction>>()
            val resolves = mutableMapOf<Instruction, List<Method>>()

            while (queue.isNotEmpty()) {
                val (prev, block, index, depth) = queue.poll()
                //val (prev, block, index) = queue.poll()
                //if (nodes.size > MAX_NODES_SIZE) break
                if (depth >= MAX_DEPTH) continue

                val current = block.instructions[index]
                if (prev?.instruction to current in visited) continue
                visited += prev?.instruction to current

                val vertex = nodes.getOrPut(current) {
                    Vertex(
                        current,
                        prev?.predecessors?.toMutableSet() ?: mutableSetOf()
                    )
                }

                if (prev?.predecessors?.contains(vertex) == true) {
                    prev.addCycleEdge(vertex)
                } else {
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

                                    val targetPackages = targets.map { it.klass.pkg }.toMutableSet()

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
                                                    it.getMethod(
                                                        currentMethod.name,
                                                        retTypeUnmapped,
                                                        *argTypesUnmapped
                                                    )
                                                }
                                            }
                                        }
                                        .filter { it.hasBody }
                                }
                            }.filter { it.hasBody }
                        }
                        var connectedExits = false
                        for (candidate in resolvedMethods) {
                            // if nodes too much stop exploring
                            if (nodes.size < MAX_NODES_SIZE) {
                                queue += QueueEntry(vertex, candidate.body.entry, 0, depth + 1)
                                queue.addAll(candidate.body.catchEntries.map { QueueEntry(null, it, 0, depth + 1) })
                            }

                            candidate.body.flatten().filterIsInstance<ReturnInst>().forEach {
                                connectedExits = true
                                val returnVertex = nodes.getOrPut(it) {
                                    Vertex(it, mutableSetOf()).also { ver ->
                                        // TODO: maybe instead of using the same link and then deleting it on expansion
                                        if (depth + 1 >= MAX_DEPTH || nodes.size >= MAX_NODES_SIZE) {
                                            vertex.linkDown(ver)
                                        }
                                    }
                                }
                                queue += QueueEntry(returnVertex, block, index + 1, depth)
                            }
                        }

                        if (!connectedExits) {
                            queue += QueueEntry(vertex, block, index + 1, depth)
                        }
                    }

                    is TerminateInst -> current.successors.forEach {
                        queue += QueueEntry(vertex, it, 0, depth)
                    }

                    else -> queue += QueueEntry(vertex, block, index + 1, depth)
                }
            }

            return nodes.getOrPut(instruction) { Vertex(instruction, mutableSetOf()) }.also { it.recomputeScore() }
        }

    }

    private var expectedTrace: List<Vertex>? = null

    fun addTrace(trace: List<Instruction>) = try {
        var prev: Vertex? = null
        for ((i, inst) in trace.withIndex()) {
            if (expectedTrace != null) {
                val expectedInstruction = expectedTrace?.getOrNull(i)?.instruction
                if (expectedInstruction == null) {
                    expectedTrace = null
                } else {
                    if (inst != expectedInstruction) {
                        changePathScoreMultiplier(expectedTrace!!.subList(0, i))
                        expectedTrace = null
                    }
                }
            }

            val current = getVertex(inst, prev)
            current.decreaseCoveredScore()

            if (prev == null) {
                prev = current
                continue
            }

            if (prev.cycleEdgesScores.contains(current)) {
                prev.decreaseCycleEdgeWeight(current)
            } else if (prev.predecessors.contains(current)) {
                prev.addCycleEdge(current)
            } else if (!prev.predecessors.contains(current)) {
                prev.linkDown(current)
            }

            prev = current
        }

        prev?.invalidate()
        targets.forEach {
            val methodRootVertex = getVertex(it.body.entry.instructions.first())
            methodRootVertex.recomputeScore()
        }
    } catch (e: Exception) {
        log.debug(e.stackTraceToString())
    }

    private fun saveScores() {
        nodes.forEach {
            it.value.saveScore()
        }
    }

    private fun restoreScores() {
        nodes.forEach {
            it.value.restoreScore()
        }
    }

    // for transforming path selector to the symbolic uncomment lines below and modify add trace
    //var scoreChangeHistory: MutableList<Pair<Int, () -> Unit>> = mutableListOf()

    fun getPath(root: Instruction): List<Vertex> {
        saveScores()
        //scoreChangeHistory = mutableListOf()

        var prev = getVertex(root)
        // var curIndex = 0

        prev.decreaseCoveredScore()
        //val refToRoot = prev
        //scoreChangeHistory.add(Pair(curIndex) { refToRoot.decreaseCoveredScore() })

        var prevUnreachableGraphVertex = rootMultiplier.downEdges[prev]
        val path = mutableListOf(prev)
        var coveredScore = 0
        while (prev.hasSuccessors() && coveredScore < MIN_COVERED_SCORE) {
            //curIndex += 1
            val nextVertex = prev.nextVertex(prevUnreachableGraphVertex) ?: break

            nextVertex.decreaseCoveredScore()
            //scoreChangeHistory.add(Pair(curIndex) { nextVertex.decreaseCoveredScore() })

            if (nextVertex.instruction in targetInstructions) {
                coveredScore += nextVertex.coveredScore
            }

            path.add(nextVertex)
            prev = nextVertex
            prevUnreachableGraphVertex = prevUnreachableGraphVertex?.downEdges?.get(nextVertex)
        }
        restoreScores()
        prev.invalidate()
        expectedTrace = path
        return path
    }

    data class MultiplierGraphVertex(
        val vertex: Vertex?,
        val downEdges: MutableMap<Vertex, MultiplierGraphVertex> = mutableMapOf(),
        var scoreMultiplier: Double = 1.0
    )

    private val rootMultiplier = MultiplierGraphVertex(null)

    fun changePathScoreMultiplier(path: List<Vertex>, scoreMultiplierChange: Double = 0.5) {
        if (path.isEmpty()) return
        var currentMultiplierVertex = rootMultiplier
        for (vertex in path) {
            val nextMultiplierVertex = currentMultiplierVertex.downEdges.getOrPut(vertex) {
                MultiplierGraphVertex(vertex)
            }
            if (nextMultiplierVertex.scoreMultiplier <= ISUFFICIENT_PATH_SCORE) return
            currentMultiplierVertex = nextMultiplierVertex
        }
        currentMultiplierVertex.vertex?.invalidate()
        currentMultiplierVertex.scoreMultiplier *= scoreMultiplierChange
        getVertex(path.first().instruction).restoreScore()
    }

//    fun addUnreachable(unreachablePath: List<Vertex>) {
//        if (unreachablePath.isEmpty()) {
//            targets.forEach {
//                val methodRootVertex = getVertex(it.body.entry.instructions.first())
//                methodRootVertex.recomputeScore()
//            }
//            return
//        }
//        restoreScores()
//        scoreChangeHistory.filter { it.first < unreachablePath.size-1 }.forEach {
//            it.second.invoke()
//        }
//        var currentUnreachableVertex = rootUnreachable
//        for (vertex in unreachablePath) {
//            val nextUnreachableGraphVertex = currentUnreachableVertex.downEdges.getOrPut(vertex) {
//                UnreachableGraphVertex(vertex)
//            }
//            // some sub-path already unreachable
//            if (nextUnreachableGraphVertex.isTerminal) return
//            currentUnreachableVertex = nextUnreachableGraphVertex
//        }
//        currentUnreachableVertex.isTerminal = true
//        currentUnreachableVertex.vertex?.invalidate()
//        targets.forEach {
//            val methodRootVertex = getVertex(it.body.entry.instructions.first())
//            methodRootVertex.recomputeScore()
//        }
//    }

}