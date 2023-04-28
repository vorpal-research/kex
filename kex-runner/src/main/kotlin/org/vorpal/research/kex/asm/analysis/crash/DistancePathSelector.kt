package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicPathSelector
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.util.PriorityQueue
import kotlin.random.nextULong

@Suppress("unused", "CanBeParameter")
class DistancePathSelector(
    private val ctx: ExecutionContext,
    private val rootMethod: Method,
    private val targetInstructions: Set<Instruction>,
    private val stackTrace: StackTrace
) : SymbolicPathSelector {
    private val distanceCounter = MethodDistanceCounter(rootMethod, targetInstructions, stackTrace)
    private val queue = PriorityQueue<Pair<TraverserState, BasicBlock>>(compareBy { (state, block) ->
        state.stackTrace.sumOf { distanceCounter.score(it.instruction.parent) } + distanceCounter.score(block)
    })

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        if (distanceCounter.score(block) < MethodDistanceCounter.INF) {
            queue += state to block
        }
    }

    override suspend fun hasNext(): Boolean = queue.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> {
        return queue.poll()
    }
}

@Suppress("unused", "CanBeParameter")
class RandomizedDistancePathSelector(
    private val ctx: ExecutionContext,
    private val rootMethod: Method,
    private val targetInstructions: Set<Instruction>,
    private val stackTrace: StackTrace
) : SymbolicPathSelector {
    private val distanceCounter = MethodDistanceCounter(
        rootMethod,
        targetInstructions,
        stackTrace
    )
    private val queue = mutableSetOf<Triple<TraverserState, BasicBlock, ULong>>()
    private var maxScore = 0UL
//    private val executionTree = ExecutionTree()

    private fun score(state: TraverserState, block: BasicBlock) =
        state.stackTrace.sumOf { distanceCounter.score(it.instruction.parent) } +
                state.stackTrace.size.toULong() * MethodDistanceCounter.CALL_WEIGHT +
                distanceCounter.score(block)

    override suspend fun add(state: TraverserState, block: BasicBlock) {
        val triple = Triple(state, block, score(state, block))
        if (triple.third >= MethodDistanceCounter.INF) return
        queue += triple
        maxScore += triple.third
//        executionTree.addTrace(state.symbolicState)
//        executionTree.view()
    }

    private val ULong.normalized: ULong get() = maxScore - this

    override suspend fun hasNext(): Boolean = queue.isNotEmpty()

    override suspend fun next(): Pair<TraverserState, BasicBlock> {
        if (queue.size == 1) {
            val triple = queue.single()
            queue -= triple
            maxScore -= triple.third
            return triple.first to triple.second
        }
        val randomWeight = ctx.random.nextULong(maxScore + 1UL)
        var currentWeight = 0UL
        for (triple in queue) {
            if (currentWeight + triple.third.normalized >= randomWeight) {
                queue.remove(triple)
                maxScore -= triple.third
                return triple.first to triple.second
            } else {
                currentWeight += triple.third.normalized
            }
        }
        return unreachable { log.error("Something went wrong with the randomized choice") }
    }
}
