package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.util.TopologicalSorter
import org.jetbrains.research.kfg.visitor.MethodVisitor

class PredicateStateBuilder(method: Method) : MethodVisitor(method), Loggable {
    val blocks = mutableMapOf<BasicBlock, PredicateState>()
    val instructionStates = mutableMapOf<Instruction, PredicateState>()

    val order = mutableListOf<BasicBlock>()

    override fun visit() {
        val (order, cycled) = TopologicalSorter(method.basicBlocks.toSet()).sort(method.getEntry())
        assert(cycled.isEmpty(), { log.error("No topological sorting for $method") })
        this.order.addAll(order.reversed())
    }

    private fun processBasicBlock(bb: BasicBlock) {

    }
}