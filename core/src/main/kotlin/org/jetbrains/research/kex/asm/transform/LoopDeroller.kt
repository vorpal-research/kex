package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.toInt
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.analysis.LoopVisitor
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.util.TopologicalSorter
import kotlin.math.abs

private val derollCount = GlobalConfig.getIntValue("loop", "deroll-count", 3)

object LoopDeroller : LoopVisitor {
    override fun cleanup() {}

    override fun visit(loop: Loop) {
        super.visit(loop)

        val method = loop.method ?: unreachable { log.error("Can't get method of loop") }
        val header = loop.header
        val preheader = loop.preheader
        val latch = loop.latch

        val terminator = run {
            var current = latch.successors.first()
            while (current.terminator is JumpInst) current = current.successors.first()
            current.terminator
        }
        val (continueOnTrue, loopExit) = when {
            terminator.successors.isEmpty() -> false to null
            terminator.successors.size == 1 -> false to null
            else -> {
                val con = terminator.successors.first() in loop
                con to terminator.successors[con.toInt()]
            }
        }

        loop.body.mapNotNull { it as? CatchBlock }.forEach { cb -> cb.getAllPredecessors().forEach { it.addSuccessor(cb) } }
        val (order, _) = TopologicalSorter(loop.body).sort(header)
        loop.body.mapNotNull { it as? CatchBlock }.forEach { cb -> cb.getAllPredecessors().forEach { it.removeSuccessor(cb) } }
        val blockOrder = order.filter { it in loop.body }.reversed()

        val currentInsts = hashMapOf<Value, Value>()
        loop.body.flatten().forEach { currentInsts[it] = it }

        var lastLatch = preheader
        var lastHeader = header

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

        val methodPhiMappings = hashMapOf<PhiInst, MutableMap<BasicBlock, Value>>()
        val methodPhis = method.filter { it !in body }.flatten().mapNotNull { it as? PhiInst }
        for (phi in methodPhis) {
            val incomings = phi.incomings.toMutableMap()
            body.forEach { incomings.remove(it) }
            methodPhiMappings[phi] = incomings
        }

        for (iteration in 0 until actualDerollCount) {
            val currentBlocks = hashMapOf<BasicBlock, BasicBlock>()
            for (block in body) {
                val newBlock = when (block) {
                    is BodyBlock -> BodyBlock("${block.name.name}.deroll")
                    is CatchBlock -> CatchBlock("${block.name.name}.deroll", block.exception)
                    else -> unreachable { log.error("Unknown block type: ${block.name}") }
                }
                currentBlocks[block] = newBlock
            }

            for ((original, derolled) in currentBlocks) {
                when (original) {
                    header -> {
                        lastLatch.replaceUsesOf(lastHeader, derolled)
                        derolled.addPredecessor(lastLatch)
                    }
                    else -> {
                        val predecessors = original.predecessors.map { currentBlocks.getValue(it) }
                        derolled.addPredecessors(*predecessors.toTypedArray())
                        predecessors.forEach { it.addSuccessor(derolled) }
                    }
                }
                if (original is CatchBlock) {
                    val throwers = original.throwers.map { currentBlocks.getValue(it) }
                    derolled as CatchBlock
                    derolled.addThrowers(throwers)
                    throwers.forEach { it.addHandler(derolled) }
                }
                for (handle in original.handlers) {
                    val derolledHandle = (currentBlocks[handle] ?: handle) as CatchBlock
                    derolled.addHandler(derolledHandle)
                    derolledHandle.addThrowers(listOf(derolled))
                }

                val successors = original.successors.map {
                    currentBlocks[it] ?: it
                }
                derolled.addSuccessors(*successors.toTypedArray())
                successors.forEach { it.addPredecessor(derolled) }
            }

            for (block in blockOrder) {
                val newBlock = currentBlocks.getValue(block)
                for (inst in block) {
                    val updated = inst.update(currentInsts)
                    updated.location = inst.location
                    currentInsts[inst] = updated
                    newBlock += updated

                    if (updated is BlockUser) {
                        currentBlocks.forEach { (original, derolled) -> updated.replaceUsesOf(original, derolled) }
                    }

                    if (updated is PhiInst && block == header) {
                        val map = mapOf(currentBlocks.getValue(latch) to lastLatch)
                        val previousMap = updated.incomings
                        map.forEach { o, t -> updated.replaceUsesOf(o, t) }

                        if (updated.predecessors.toSet().size == 1) {
                            val actual = previousMap.getValue(lastLatch)
                            updated.replaceAllUsesWith(actual)
                            currentInsts[inst] = actual
                        }
                    }
                }

                for (phi in methodPhis) {
                    if (phi.incomings.contains(block)) {
                        val value = phi.incomings[block]!!
                        methodPhiMappings[phi]?.put(currentBlocks.getValue(block), currentInsts.getOrDefault(value, value))
                    }
                }
            }

            lastLatch = currentBlocks.getValue(latch)
            lastHeader = currentBlocks.getValue(header)

            currentBlocks.forEach { loop.addBlock(it.value) }

            val phiMappings = blockOrder
                    .flatten()
                    .zip(blockOrder.map { currentBlocks.getValue(it) }.flatten())
                    .filter { it.first is PhiInst }

            blockOrder.forEach {
                val mapping = currentBlocks.getValue(it)
                method.addBefore(header, mapping)
                if (mapping is CatchBlock) method.addCatchBlock(mapping)
            }
            for ((_, derolled) in currentBlocks) loop.parent?.addBlock(derolled)

            for ((orig, new) in phiMappings) {
                if (new is PhiInst) {
                    val bb = new.parent!!
                    val incomings = new.incomings.filter { it.key in bb.predecessors }
                    val phiValue = when {
                        incomings.size == 1 -> {
                            bb -= new
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
        }

        if (loopExit != null) {
            lastLatch.replaceUsesOf(lastHeader, loopExit)
            loopExit.addPredecessor(lastLatch)
        }
        val unreachableBlock = BodyBlock("unreachable")
        unreachableBlock.add(IF.getUnreachable())
        if (lastLatch.terminator.successors.size == 1) {
            lastLatch.replaceUsesOf(loopExit!!, unreachableBlock)
        } else {
            val block = lastLatch.terminator.successors[(!continueOnTrue).toInt()]
            lastLatch.replaceUsesOf(block, unreachableBlock)
        }
        method.add(unreachableBlock)

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
        for (phi in methodPhis) {
            val newPhi = IF.getPhi(phi.type, methodPhiMappings.getValue(phi))
            newPhi.location = phi.location

            val bb = phi.parent!!
            bb.insertBefore(phi, newPhi)
            phi.replaceAllUsesWith(newPhi)
            bb -= phi
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