package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicPathSelector
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import java.util.PriorityQueue

class DistancePathSelector(
    private val rootMethod: Method,
    private val targetInstructions: Set<Instruction>,
    private val stackTrace: StackTrace
) : SymbolicPathSelector {
    private val distanceCounter = MethodDistanceCounter(stackTrace)
    private val queue = PriorityQueue<Pair<TraverserState, BasicBlock>>(compareBy { (state, block) ->
        state.stackTrace.sumOf { distanceCounter.score(it.instruction.parent) } + distanceCounter.score(block)
    })

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        queue += state to block
    }

    override suspend fun hasNext(): Boolean = queue.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> {
        return queue.poll()
    }
}
