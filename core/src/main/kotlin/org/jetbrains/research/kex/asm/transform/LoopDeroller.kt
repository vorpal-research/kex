package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.ir.viewCfg
import org.jetbrains.research.kfg.util.TopologicalSorter
import org.jetbrains.research.kfg.visitor.LoopVisitor
import kotlin.math.abs

val derollCount = GlobalConfig.getIntValue("loop", "deroll-count", 3)

class LoopDeroller(method: Method) : LoopVisitor(method), Loggable {
    override fun visitLoop(loop: Loop) {
        super.visitLoop(loop)
        val header = loop.header
        val preheader = loop.getPreheader()
        val latch = loop.getLatch()
//        assert(header.successors.size == 2, { log.error("Loop header have too many successors") })
        val exit = loop.getLoopExits().first()

        val (order, _) = TopologicalSorter(loop.body).sort(header)
        val blockOrder = order.filter { it in loop.body }.reversed()

        val currentInsts = mutableMapOf<Value, Value>()
        loop.body.flatten().forEach { currentInsts[it] = it }

        var predecessor = preheader
        var prevHeader = header
        val tripCount = getConstantTripCount(loop)

        val body = loop.body.toTypedArray()
        body.forEach { loop.removeBlock(it) }

        val loopThrowers = method.catchEntries.map { catch ->
            catch to body.filter { it.handlers.contains(catch) }
        }.toMap()

        val actualDerollCount = if (tripCount > 0) tripCount else derollCount
        log.debug("Method $method, unrolling loop $loop to $actualDerollCount iterations")
        for (iteration in 0 until actualDerollCount) {
            val currentBlocks = mutableMapOf<BasicBlock, BasicBlock>()
            for (block in body) currentBlocks[block] = BodyBlock("${block.name.name}.deroll")

            for ((original, derolled) in currentBlocks) {
                if (original == header) {
                    predecessor.getTerminator().replaceUsesOf(prevHeader, derolled)
                    predecessor.removeSuccessor(prevHeader)
                    predecessor.addSuccessors(derolled)
                    derolled.addPredecessor(predecessor)
                } else {
                    val predecessors = original.predecessors.map { currentBlocks.getValue(it) }
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
                val newBlock = currentBlocks.getValue(block)
                for (inst in block) {
                    val updated = inst.update(currentInsts)
                    currentInsts[inst] = updated
                    newBlock.addInstruction(updated)
                    if (updated is BlockUser) {
                        currentBlocks.forEach { (original, derolled) -> updated.replaceUsesOf(original, derolled) }
                    }
                    if (updated is PhiInst && block == header) {
                        mapOf(currentBlocks.getValue(latch) to predecessor).forEach { o, t -> updated.replaceUsesOf(o, t) }
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

            predecessor = currentBlocks.getValue(latch)
            prevHeader = currentBlocks.getValue(header)

            currentBlocks.forEach {
                loop.addBlock(it.value)
            }
            loopThrowers.forEach { catch, throwers ->
                val derolledThrowers = throwers.map { currentBlocks.getValue(it) }
                derolledThrowers.forEach {
                    it.addHandler(catch)
                    catch.addThrowers(listOf(it))
                }
            }
            body.forEach {
                method.addBefore(header, currentBlocks.getValue(it))
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
        loopThrowers.forEach { catch, throwers ->
            throwers.forEach {
                catch.removeThrower(it)
                it.removeHandler(catch)
            }
        }
    }

    override fun preservesLoopInfo() = false

    private fun getConstantTripCount(loop: Loop): Int {
        val header = loop.header
        val preheader = loop.getPreheader()
        val latch = loop.getLatch()

        val branch = header.getTerminator() as? BranchInst ?: return -1
        val cmp = branch.getCond() as? CmpInst ?: return -1
        val (value, max) =
                if (cmp.getLhv() is IntConstant) cmp.getRhv() to (cmp.getLhv() as IntConstant)
                else if (cmp.getRhv() is IntConstant) cmp.getLhv() to (cmp.getRhv() as IntConstant)
                else return -1

        val (init, updated) = if (value is PhiInst) {
            val incomings = value.getIncomings()
            assert(incomings.size == 2, { log.error("Unexpected number of header incomings") })
            incomings.getValue(preheader) to incomings.getValue(latch)
        } else return -1

        if (init !is IntConstant) return -1

        val update = if (updated is BinaryInst) {
            val (updateValue, updateSize) =
                    if (updated.getRhv() is IntConstant) updated.getLhv() to (updated.getRhv() as IntConstant)
                    else if (updated.getLhv() is IntConstant) updated.getRhv() to (updated.getLhv() as IntConstant)
                    else return -1
            if (updateValue != value) return -1
            if (init.value > max.value && updated.opcode is BinaryOpcode.Sub) updateSize
            else if (init.value < max.value && updated.opcode is BinaryOpcode.Add) updateSize
            else return -1
        } else return -1

        return abs((max.value - init.value) / update.value)
    }
}