package org.vorpal.research.kex.asm.analysis.symbolic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vorpal.research.kex.asm.analysis.util.SuspendableIterator
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kthelper.collection.queueOf
import java.io.File
import kotlin.math.min

interface SymbolicPathSelector : SuspendableIterator<Pair<TraverserState, BasicBlock>> {
    suspend fun add(state: TraverserState, block: BasicBlock)

    suspend operator fun plusAssign(data: Pair<TraverserState, BasicBlock>) = add(data.first, data.second)
}

/**
 * Implements bfs algorithm
 */
class DequePathSelector : SymbolicPathSelector {
    private val queue = queueOf<Pair<TraverserState, BasicBlock>>()

    init {
        val file = File("/Users/ravsemirnov/Desktop/JetBrains/projects/kex/checkPathSelector.txt")
        val writer = file.bufferedWriter()
        writer.write("I USE DequePathSelector")
    }

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        queue += state to block
        val file = File("/Users/ravsemirnov/Desktop/JetBrains/projects/kex/checkPathSelector.txt")
        val writer = file.bufferedWriter()
        withContext(Dispatchers.IO) {
            writer.write("I USE DequePathSelector")
        }
    }

    override suspend fun hasNext(): Boolean = queue.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> = queue.poll()
}

/**
 * Implements n-subpath algorithm, which makes path decisions based on the frequency of visits to paths of length n
 */
class NSubpathPathSelector(val n: Int = 2) : SymbolicPathSelector {
    private val eSVector = mutableSetOf<ExecutionState>()
    private val pathVisits = mutableMapOf<List<BasicBlock>, Int>()

    init {
        val file = File("/Users/ravsemirnov/Desktop/JetBrains/projects/kex/checkPathSelector.txt")
        val writer = file.bufferedWriter()
        writer.write("I USE NSubpathPathSelector")
    }

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        val newState = ExecutionState(state to block, n)
        eSVector.add(newState)
        if (pathVisits[newState.path] == null) {
            pathVisits[newState.path] = 0
        }
    }

    override suspend fun hasNext(): Boolean = eSVector.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> {
        var minPath = Int.MAX_VALUE
        eSVector.forEach { minPath = min(minPath, pathVisits[it.path]!!) }
        val selectSet = mutableSetOf<ExecutionState>()

        for (elem in eSVector) {
            if (pathVisits[elem.path] == minPath) {
                selectSet.add(elem)
            }
        }

        val result =  selectSet.random()
        pathVisits[result.path] = pathVisits[result.path]!! + 1
        eSVector.remove(result)
        return result.node
    }

    class ExecutionState(
        val node: Pair<TraverserState, BasicBlock>,
        pathLength: Int
    ) {
        val path: List<BasicBlock> = node.first.blockPath.takeLast(pathLength)
    }
}

/**
 * Implements bfs algorithm, which takes into account frequency of visits to paths of length n from n-subpath
 */
class PriorityDequePathSelector(val n: Int = 2) : SymbolicPathSelector {
    private val eSVector = mutableSetOf<ExecutionState>()
    private val p = mutableMapOf<List<BasicBlock>, Int>()

    init {
        val file = File("/Users/ravsemirnov/Desktop/JetBrains/projects/kex/checkPathSelector.txt")
        val writer = file.bufferedWriter()
        writer.write("I USE PriorityDequePathSelector")
    }

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        val newState = ExecutionState(state to block, n)
        eSVector.add(newState)
        if (p[newState.path] == null) {
            p[newState.path] = 0
        }
    }

    override suspend fun hasNext(): Boolean = eSVector.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> {
        val minLen = eSVector.minOf { it.path.size }
        val filteredESVector = eSVector.filter { it.path.size == minLen }

        var minPath = Int.MAX_VALUE
        filteredESVector.forEach { minPath = min(minPath, p[it.path]!!) }
        val filteredESVector2 = filteredESVector.filter { p[it.path] == minPath }

        val result =  filteredESVector2.random()

        p[result.path] = p[result.path]!! + 1
        eSVector.remove(result)
        return result.node
    }

    class ExecutionState(
        val node: Pair<TraverserState, BasicBlock>,
        pathLength: Int
    ) {
        val path: List<BasicBlock> = node.first.blockPath.takeLast(pathLength)
    }
}