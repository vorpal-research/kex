package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.util.TopologicalSorter
import org.jetbrains.research.kfg.visitor.LoopVisitor
import kotlin.math.abs

val derollCount = GlobalConfig.getIntValue("loop", "deroll-count", 3)

class LoopDeroller(method: Method) : LoopVisitor(method) {
    override fun visitLoop(loop: Loop) {
        super.visitLoop(loop)
        val header = loop.header
        val preheader = loop.preheader
        val latch = loop.latch
        val exit = loop.loopExits.first()

        val (order, _) = TopologicalSorter(loop.body).sort(header)
        val blockOrder = order.filter { it in loop.body }.reversed()

        val currentInsts = hashMapOf<Value, Value>()
        loop.body.flatten().forEach { currentInsts[it] = it }

        var predecessor = preheader
        var prevHeader = header
        val tripCount = getConstantTripCount(loop)

        val body = loop.body.toTypedArray()
        body.forEach { loop.removeBlock(it) }

        val loopThrowers = method.catchEntries.map { catch ->
            catch to body.filter { it.handlers.contains(catch) }
        }.toMap()

        val actualDerollCount = when {
            tripCount > 0 -> tripCount
            else -> derollCount
        }
        log.debug("Method $method, unrolling loop $loop to $actualDerollCount iterations")

        for (iteration in 0 until actualDerollCount) {
            val currentBlocks = hashMapOf<BasicBlock, BasicBlock>()
            for (block in body) currentBlocks[block] = BodyBlock("${block.name.name}.deroll")

            for ((original, derolled) in currentBlocks) {
                when (original) {
                    header -> {
                        predecessor.terminator.replaceUsesOf(prevHeader, derolled)
                        predecessor.removeSuccessor(prevHeader)
                        predecessor.addSuccessors(derolled)
                        derolled.addPredecessor(predecessor)
                    }
                    else -> {
                        val predecessors = original.predecessors.map { currentBlocks.getValue(it) }
                        derolled.addPredecessors(*predecessors.toTypedArray())
                        predecessors.forEach { it.addSuccessor(derolled) }
                    }
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
                    log.debug("Old inst: ${inst.print()}")
                    log.debug("Updated inst: ${updated.print()}")
                    updated.location = inst.location
                    currentInsts[inst] = updated
                    newBlock.addInstruction(updated)

                    if (updated is BlockUser) {
                        currentBlocks.forEach { (original, derolled) -> updated.replaceUsesOf(original, derolled) }
                    }

                    if (updated is PhiInst && block == header) {
                        val map = mapOf(currentBlocks.getValue(latch) to predecessor)
                        val previousMap = updated.incomings
                        map.forEach { o, t -> updated.replaceUsesOf(o, t) }

                        if (updated.predecessors.toSet().size == 1) {
                            val actual = previousMap.getValue(predecessor)
                            log.debug("Updeted inst is changed to incoming from ${predecessor.name}")
                            updated.replaceAllUsesWith(actual)
                            currentInsts[inst] = actual
                        }
                    }
                }
            }

            predecessor = currentBlocks.getValue(latch)
            prevHeader = currentBlocks.getValue(header)

            currentBlocks.forEach { loop.addBlock(it.value) }
            for ((catch, throwers) in loopThrowers) {
                val derolledThrowers = throwers.map { currentBlocks.getValue(it) }
                derolledThrowers.forEach {
                    it.addHandler(catch)
                    catch.addThrowers(listOf(it))
                }
            }

            val phiMappings = blockOrder
                    .flatten()
                    .zip(blockOrder.map { currentBlocks.getValue(it) }.flatten())
                    .filter { it.first is PhiInst }

            for ((orig, new) in phiMappings) {
                if (new is PhiInst) {
                    val bb = new.parent!!
                    val incomings = new.incomings.filter { it.key in bb.predecessors }
                    val phiValue = when {
                        incomings.size == 1 -> {
                            bb.remove(new)
                            incomings.values.first()
                        }
                        else -> {
                            val newPhi = IF.getPhi(new.type, incomings)
                            bb.replace(new, newPhi)
                            newPhi
                        }
                    }
                    currentInsts[orig] = phiValue
                    new.replaceAllUsesWith(phiValue)
                }
            }

            blockOrder.forEach { method.addBefore(header, currentBlocks.getValue(it)) }
            for ((_, derolled) in currentBlocks) loop.parent?.addBlock(derolled)
        }

        predecessor.replaceSuccessorUsesOf(prevHeader, exit)
        predecessor.terminator.replaceUsesOf(prevHeader, exit)
        predecessor.addSuccessors(exit)

        for (block in body) {
            block.predecessors.toTypedArray().forEach { block.removePredecessor(it) }
            block.successors.toTypedArray().forEach { block.removeSuccessor(it) }
            method.remove(block)
        }

        for ((catch, throwers) in loopThrowers) {
            throwers.forEach {
                catch.removeThrower(it)
                it.removeHandler(catch)
            }
        }
    }

    override fun preservesLoopInfo() = false

    private fun getConstantTripCount(loop: Loop): Int {
        val header = loop.header
        val preheader = loop.preheader
        val latch = loop.latch

        val branch = header.terminator as? BranchInst ?: return -1
        val cmp = branch.cond as? CmpInst ?: return -1
        val (value, max) = when {
            cmp.lhv is IntConstant -> cmp.rhv to (cmp.lhv as IntConstant)
            cmp.rhv is IntConstant -> cmp.lhv to (cmp.rhv as IntConstant)
            else -> return -1
        }

        val (init, updated) = when (value) {
            is PhiInst -> {
                val incomings = value.incomings
                require(incomings.size == 2) { log.error("Unexpected number of header incomings") }
                incomings.getValue(preheader) to incomings.getValue(latch)
            }
            else -> return -1
        }

        if (init !is IntConstant) return -1

        val update = when (updated) {
            is BinaryInst -> {
                val (updateValue, updateSize) = when {
                    updated.rhv is IntConstant -> updated.lhv to (updated.rhv as IntConstant)
                    updated.lhv is IntConstant -> updated.rhv to (updated.lhv as IntConstant)
                    else -> return -1
                }
                when {
                    updateValue != value -> return -1
                    init.value > max.value && updated.opcode is BinaryOpcode.Sub -> updateSize
                    init.value < max.value && updated.opcode is BinaryOpcode.Add -> updateSize
                    else -> return -1
                }
            }
            else -> return -1
        }

        return abs((max.value - init.value) / update.value)
    }
}