package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kex.algorithm.DominatorTree
import org.jetbrains.research.kex.algorithm.DominatorTreeBuilder
import org.jetbrains.research.kex.algorithm.TopologicalSorter
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.util.*

class PredicateStateAnalysis(method: Method) : MethodVisitor(method), Loggable {
    private val blockStates = mutableMapOf<BasicBlock, PredicateState>()
    private val instructionStates = mutableMapOf<Instruction, PredicateState>()
    private val initialState = BasicState()

    private val order = mutableListOf<BasicBlock>()
    private val domTree = DominatorTree<BasicBlock>()
    private val predicateBuilder = PredicateBuilder(method)

    fun getInstructionState(inst: Instruction): PredicateState? = instructionStates.getOrElse(inst) {
        val active = mutableSetOf<BasicBlock>()
        val queue = ArrayDeque<BasicBlock>()
        queue.push(inst.parent ?: unreachable { log.error("Trying to get state for instruction without parent") })
        while (queue.isNotEmpty()) {
            val current = queue.first
            if (current !in active) {

                active.add(current)
                for (predecessor in current.predecessors) {
                    if (!instructionStates.containsKey(predecessor.getTerminator())) queue.addLast(predecessor)
                }
            }
            queue.pop()
        }
        order.filter { it in active }.forEach { processBasicBlock(it) }
        instructionStates[inst]
    }

    override fun visit() {
        predicateBuilder.visit()
        if (method.isAbstract()) return
        val (order, cycled) = TopologicalSorter(method.basicBlocks.toSet()).sort(method.getEntry())
        domTree.putAll(DominatorTreeBuilder(method.basicBlocks.toSet()).build())
        assert(cycled.isEmpty()) { log.error("No topological sorting for $method") }
        this.order.addAll(order.reversed())
    }

    private fun processBasicBlock(bb: BasicBlock) {
        if (bb in method.getCatchBlocks()) return
        var inState = getBlockEntryState(bb) ?: return

        for (inst in bb) {
            val predicate = predicateBuilder.predicateMap[inst]
            val instState = if (predicate != null) (StateBuilder(inState) + predicate).apply() else inState
            instructionStates[inst] = instState

            inState = instState
        }

        blockStates[bb] = inState
    }

    private fun getBlockEntryState(bb: BasicBlock): PredicateState? {
        if (bb in method.getCatchBlocks()) return unreachable { log.error("Catch blocks are not supported yet") }

        val idom = domTree.getIdom(bb) ?: return initialState

        val base = blockStates[idom.value] ?: return null
        val choices = mutableListOf<PredicateState>()

        for (predecessor in bb.predecessors) {
            val predState = StateBuilder(blockStates[predecessor] ?: continue)

            val terminatorPredicate = predicateBuilder.terminatorPredicateMap[bb to predecessor.getTerminator()]
            if (terminatorPredicate != null) predState += terminatorPredicate

            for (phi in bb.instructions.mapNotNull { it as? PhiInst }) {
                predState += predicateBuilder.phiPredicateMap.getValue(predecessor to phi)
            }

            val sliced = predState.apply().sliceOn(base)
                    ?: unreachable { log.error("Cannot slice state on it's predecessor") }
            choices.add(sliced)
        }

        return when {
            choices.isEmpty() -> null
            choices.size == 1 -> (StateBuilder(base) + choices.first()).apply()
            else -> (StateBuilder(base) + ChoiceState(choices)).apply()
        }
    }
}