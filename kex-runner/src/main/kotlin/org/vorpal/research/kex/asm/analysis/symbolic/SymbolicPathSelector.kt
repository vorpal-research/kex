package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kex.asm.analysis.util.SuspendableIterator
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kthelper.collection.queueOf
import kotlin.math.min

interface SymbolicPathSelector : SuspendableIterator<Pair<TraverserState, BasicBlock>> {
    suspend fun add(state: TraverserState, block: BasicBlock)

    suspend operator fun plusAssign(data: Pair<TraverserState, BasicBlock>) = add(data.first, data.second)
}

class DequePathSelector : SymbolicPathSelector {
    private val queue = queueOf<Pair<TraverserState, BasicBlock>>()

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        queue += state to block
    }

    override suspend fun hasNext(): Boolean = queue.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> = queue.poll()
}

class NSubpathPathSelector(val n: Int = 2) : SymbolicPathSelector {
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
        var minPath = Int.MAX_VALUE
        eSVector.forEach { minPath = min(minPath, p[it.path]!!) }
        val selectSet = mutableSetOf<ExecutionState>()

        for (elem in eSVector) {
            if (p[elem.path] == minPath) {
                selectSet.add(elem)
            }
        }

        val result =  selectSet.random()
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
