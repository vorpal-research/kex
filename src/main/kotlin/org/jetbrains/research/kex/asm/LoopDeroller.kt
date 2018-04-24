package org.jetbrains.research.kex.asm

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.util.GraphNode
import org.jetbrains.research.kfg.util.TopologicalSorter
import org.jetbrains.research.kfg.visitor.LoopVisitor

val derollCount = GlobalConfig.getIntValue("loop.deroll-count", 3)

class LoopDeroller(method: Method) : LoopVisitor(method), Loggable {
    override fun visitLoop(loop: Loop) {
        super.visitLoop(loop)
        val header = loop.header
        val preheader = loop.getPreheader()
        val latch = loop.getLatch()
        assert(header.successors.size == 2, { log.debug("Loop header have too many successors") })
        val exit = loop.getLoopExits().first()

        val nodes = loop.body.map { it as GraphNode }.toSet()
        val (order, _) = TopologicalSorter(nodes).sort(header)
        val blockOrder = order.map { it as BasicBlock }.filter { it in loop.body }.reversed()

        val currentInsts = mutableMapOf<Value, Value>()
        loop.body.flatten().forEach { currentInsts[it] = it }

        var predecessor = preheader
        var prevHeader = header

        val body = loop.body.toTypedArray()
        body.forEach { loop.removeBlock(it) }

        for (iteration in 0 until derollCount) {
            val currentBlocks = mutableMapOf<BasicBlock, BasicBlock>()
            for (block in body) currentBlocks[block] = BodyBlock("${block.name.name}.deroll")

            for ((original, derolled) in currentBlocks) {
                if (original == header) {
                    predecessor.getTerminator().replaceUsesOf(prevHeader, derolled)
                    predecessor.removeSuccessor(prevHeader)
                    predecessor.addSuccessors(derolled)
                    derolled.addPredecessor(predecessor)
                } else {
                    val predecessors = original.predecessors.map { currentBlocks[it]!! }
                    derolled.addPredecessors(*predecessors.toTypedArray())
                    predecessors.forEach { it.addSuccessor(derolled) }
                }

                val successors = original.successors.map {
                    currentBlocks[it] ?: it
                }.filterNot { it == currentBlocks[header] }
                derolled.addSuccessors(*successors.toTypedArray())
                successors.forEach { it.addPredecessor(derolled) }
            }
            for (block in blockOrder) {
                val newBlock = currentBlocks[block]!!
                for (inst in block) {
                    val updated = inst.update(currentInsts)
                    currentInsts[inst] = updated
                    newBlock.addInstruction(updated)
                    if (updated is BlockUser) {
                        currentBlocks.forEach { (original, derolled) -> updated.replaceUsesOf(original, derolled) }
                    }
                    if (updated is PhiInst && block == header) {
                        mapOf(currentBlocks[latch]!! to predecessor).forEach { o, t -> updated.replaceUsesOf(o, t) }
                    }
                }
            }
            for (it in currentBlocks.values.flatten()) {
                if (it is PhiInst) {
                    val bb = it.parent!!
                    val incomings = it.getIncomings().filter { it.key in bb.predecessors }
                    val phiValue = if (incomings.size == 1) {
                        bb.remove(it)
                        incomings.values.first()
                    } else {
                        val newPhi = IF.getPhi(it.type, incomings)
                        bb.replace(it, newPhi)
                        newPhi
                    }
                    currentInsts[it] = phiValue
                    it.replaceAllUsesWith(phiValue)
                }
            }

            predecessor = currentBlocks[latch]!!
            prevHeader = currentBlocks[header]!!

            currentBlocks.forEach {
                loop.addBlock(it.value)
            }
            body.forEach {
                method.addBefore(header, currentBlocks[it]!!)
            }
            for ((_, derolled) in currentBlocks) loop.parent?.addBlock(derolled)
        }
        predecessor.replaceUsesOf(prevHeader, exit)
        predecessor.getTerminator().replaceUsesOf(prevHeader, exit)
        predecessor.removeSuccessor(prevHeader)
        predecessor.addSuccessors(exit)
        body.forEach { block ->
            block.predecessors.toTypedArray().forEach {
                block.removePredecessor(it)
            }
            block.successors.toTypedArray().forEach {
                block.removeSuccessor(it)
            }
            method.remove(block)
        }
    }

    override fun preservesLoopInfo() = false
}