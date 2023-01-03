package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kex.asm.analysis.util.SuspendableIterator
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kthelper.collection.queueOf

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
