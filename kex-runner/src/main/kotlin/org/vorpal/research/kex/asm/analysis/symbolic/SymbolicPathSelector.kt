package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kex.asm.analysis.util.SuspendableIterator
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kthelper.collection.queueOf

interface SymbolicPathSelector : SuspendableIterator<Pair<TraverserState, BasicBlock>> {
    suspend fun add(state: TraverserState, block: BasicBlock)

    suspend operator fun plusAssign(data: Pair<TraverserState, BasicBlock>) = add(data.first, data.second)
}

/**
 * Implements bfs algorithm
 */
class BFS : SymbolicPathSelector {
    private val queue = queueOf<Pair<TraverserState, BasicBlock>>()

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        queue += state to block
    }

    override suspend fun hasNext(): Boolean = queue.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> = queue.poll()
}

/**
 * Implements n-subPath algorithm, which makes path decisions based on the frequency of visits to paths of length n
 */
class SGS(private val n: Int = 2) : SymbolicPathSelector {
    private val eSVector = mutableSetOf<ExecutionState>()
    private val pathVisits = mutableMapOf<List<BasicBlock>, Int>()

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        val newState = ExecutionState(state to block, n)
        eSVector.add(newState)
        if (pathVisits[newState.path] == null) {
            pathVisits[newState.path] = 0
        }
    }

    override suspend fun hasNext(): Boolean = eSVector.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> {
        val minPath = eSVector.minOf { pathVisits[it.path]!! }

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
 * Implements bfs algorithm, which takes into account frequency of visits to paths of length n from n-subPath
 */
class PriorityBFS(private val n: Int = 2) : SymbolicPathSelector {
    private val eSVector = mutableSetOf<ExecutionState>()
    private val p = mutableMapOf<List<BasicBlock>, Int>()

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

        val minPath = filteredESVector.minOf { p[it.path]!! }
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
