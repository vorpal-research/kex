package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kthelper.collection.queueOf

interface PathSelector {
    fun add(state: TraverserState, block: BasicBlock)
    fun hasNext(): Boolean
    fun next(): Pair<TraverserState, BasicBlock>

    operator fun plusAssign(data: Pair<TraverserState, BasicBlock>) = add(data.first, data.second)
}

class DequePathSelector : PathSelector {
    private val queue = queueOf<Pair<TraverserState, BasicBlock>>()

    override fun add(state: TraverserState, block: BasicBlock) {
        queue += state to block
    }

    override fun hasNext(): Boolean = queue.isNotEmpty()

    override fun next(): Pair<TraverserState, BasicBlock> = queue.poll()


}
