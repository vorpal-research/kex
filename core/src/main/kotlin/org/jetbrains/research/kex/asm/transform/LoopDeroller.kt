package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kthelper.algorithm.GraphTraversal
import org.jetbrains.research.kthelper.algorithm.NoTopologicalSortingException
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.toInt
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.analysis.LoopVisitor
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.NullConstant
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import kotlin.math.abs
import kotlin.math.min

private val derollCount = kexConfig.getIntValue("loop", "deroll-count", 3)
private val maxDerollCount = kexConfig.getIntValue("loop", "max-deroll-count", 0)

class LoopDeroller(override val cm: ClassManager) : LoopVisitor {

    companion object {
        const val DEROLLED_POSTFIX = ".deroll"

        val blockMapping = hashMapOf<Method, MutableMap<BasicBlock, BasicBlock>>()
        val unreachableBlocks = hashMapOf<Method, MutableSet<BasicBlock>>()
    }

    class InvalidLoopException : Exception()

    private data class State(
            val header: BasicBlock,
            val latch: BasicBlock,
            val terminatingBlock: BasicBlock,
            val continueOnTrue: Boolean,
            var lastHeader: BasicBlock,
            var lastLatch: BasicBlock,
            val blockMappings: MutableMap<BasicBlock, BasicBlock>,
            val instMappings: MutableMap<Value, Value>
    ) {
        companion object {
            fun createState(loop: Loop): State {
                val terminatingBlock = run {
                    var current = loop.latch
                    while (current.terminator is JumpInst) current = current.successors.first()
                    current
                }
                val continueOnTrue = when {
                    terminatingBlock.terminator.successors.isEmpty() -> false
                    terminatingBlock.terminator.successors.size == 1 -> false
                    else -> terminatingBlock.terminator.successors.first() in loop
                }

                val state = State(loop.header, loop.latch, terminatingBlock,
                        continueOnTrue, loop.header, loop.preheader,
                        hashMapOf(), hashMapOf())
                loop.body.flatten().forEach { state[it] = it }

                return state
            }
        }

        operator fun set(block: BasicBlock, value: BasicBlock) {
            blockMappings[block] = value
        }

        operator fun set(value: Value, newValue: Value) {
            instMappings[value] = newValue
        }

        operator fun get(block: BasicBlock) = blockMappings[block] ?: block
        operator fun get(value: Value) = instMappings.getValue(value)

        fun getOrDefault(block: BasicBlock, default: BasicBlock) = blockMappings.getOrDefault(block, default)
        fun getOrDefault(value: Value, default: Value) = instMappings.getOrDefault(value, default)
    }


    override fun cleanup() {}

    override val preservesLoopInfo get() = false

    override fun visit(method: Method) {
        try {
            super.visit(method)
        } catch (e: InvalidLoopException) {
            log.error("Can't deroll loops of method $method")
        } catch (e: NoTopologicalSortingException) {
            log.error("Can't perform topological sorting of loops of method $method")
        }
    }

    override fun visit(loop: Loop) {
        super.visit(loop)
        if (loop.allEntries.size != 1) throw InvalidLoopException()
        if (loop.loopExits.isEmpty()) throw InvalidLoopException()

        // init state
        val method = loop.method ?: unreachable { log.error("Can't get method of loop") }
        val blockOrder = getBlockOrder(loop)
        val state = State.createState(loop)
        val derollCount = getDerollCount(loop)
        val body = loop.body.toMutableList().onEach { loop.removeBlock(it) }

        log.debug("Method $method, unrolling loop $loop to $derollCount iterations")

        // save current phi instructions of method
        val methodPhis = method.filter { it !in body }.flatten().mapNotNull { it as? PhiInst }
        val methodPhiMappings = methodPhis.map { phi ->
            phi to phi.incomings.filterNot { it.key in body }.toMutableMap()
        }.toMap().toMutableMap()

        val methodBlockMapping = blockMapping.getOrPut(method, ::mutableMapOf)

        // deroll loop for given number of iterations
        for (iteration in 0..derollCount) {
            state.blockMappings.clear()
            for (block in blockOrder) {
                val copy = copyBlock(block)
                state[block] = copy
                if (loop.hasParent) {
                    loop.parent.addBlock(copy)
                }
                methodBlockMapping[copy] = block
            }

            for (block in blockOrder) {
                copyBlockConnections(state, block)
            }

            for (block in blockOrder) {
                copyBlockInstructions(state, block)

                for (phi in methodPhis) {
                    if (phi.incomings.contains(block)) {
                        val value = phi.incomings.getValue(block)
                        methodPhiMappings[phi]?.put(state[block], state.getOrDefault(value, value))
                    }
                }
            }

            state.lastLatch = state[state.latch]
            state.lastHeader = state[state.header]

            for (block in blockOrder) {
                val mapping = state[block]
                method.addBefore(state.header, mapping)
                if (mapping is CatchBlock) method.addCatchBlock(mapping)
            }

            val phiMappings = blockOrder
                    .flatten()
                    .zip(blockOrder.map { state[it] }.flatten())
                    .filter { it.first is PhiInst }
            remapBlockPhis(state, phiMappings)
        }

        val unreachableBlock = BodyBlock("unreachable")
        unreachableBlock.add(instructions.getUnreachable())
        method.add(unreachableBlock)
        unreachableBlocks.getOrPut(method, ::hashSetOf).add(unreachableBlock)

        // remap blocks of last iteration to actual method blocks
        val lastTerminator = state[state.terminatingBlock]
        val continueBlock = lastTerminator.terminator.successors[(!state.continueOnTrue).toInt()]
        lastTerminator.replaceSuccessorUsesOf(continueBlock, unreachableBlock)
        for (block in blockOrder.reversed()) {
            if (block == state.terminatingBlock) break
            val copy = state[block]
            copy.predecessors.toTypedArray().forEach { it.removeSuccessor(copy) }
            copy.successors.toTypedArray().forEach { it.removePredecessor(copy) }
            method.remove(copy)
        }

        // cleanup loop
        cleanupBody(method, body)
        remapMethodPhis(methodPhis, methodPhiMappings)
    }

    private fun getMinDerollCount(count: Int): Int = when {
        maxDerollCount > 0 -> min(maxDerollCount, count)
        else -> count
    }

    private fun getDerollCount(loop: Loop): Int {
        val tripCount = getConstantTripCount(loop)
        return when {
            tripCount > 0 -> getMinDerollCount(tripCount)
            else -> getMinDerollCount(derollCount)
        }
    }

    private fun getOpcodeConstant(opcode: CmpOpcode, constant: IntConstant) = when (opcode) {
        is CmpOpcode.Le, is CmpOpcode.Ge -> constant
        else -> values.getIntConstant(constant.value + 1) as IntConstant
    }

    private fun getConstantTripCount(loop: Loop): Int {
        val header = loop.header
        val preheader = loop.preheader
        val latch = loop.latch

        val branch = header.terminator as? BranchInst ?: return -1
        val cmp = branch.cond as? CmpInst ?: return -1
        val (value, max) = when {
            cmp.lhv is IntConstant -> cmp.rhv to getOpcodeConstant(cmp.opcode, cmp.lhv as IntConstant)
            cmp.rhv is IntConstant -> cmp.lhv to getOpcodeConstant(cmp.opcode, cmp.rhv as IntConstant)
            else -> return -1
        }

        val (init, updated) = when (value) {
            is PhiInst -> {
                if (value.parent != header) return -1

                val incomings = value.incomings
                if (incomings.size != 2) return -1
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

    private fun getBlockOrder(loop: Loop): List<BasicBlock> {
        val latch = loop.latch
        val header = loop.header
        latch.removeSuccessor(header)
        loop.body.mapNotNull { it as? CatchBlock }.forEach { cb -> cb.allPredecessors.forEach { it.addSuccessor(cb) } }
        val order = GraphTraversal(loop).topologicalSort()
        loop.body.mapNotNull { it as? CatchBlock }.forEach { cb -> cb.allPredecessors.forEach { it.removeSuccessor(cb) } }
        latch.addSuccessor(header)
        return order.map { it.block }
    }

    private fun copyBlock(block: BasicBlock) = when (block) {
        is BodyBlock -> BodyBlock("${block.name.name}$DEROLLED_POSTFIX")
        is CatchBlock -> CatchBlock("${block.name.name}$DEROLLED_POSTFIX", block.exception)
    }

    private fun copyBlockConnections(state: State, original: BasicBlock) {
        val derolled = state[original]
        when (original) {
            state.header -> {
                state.lastLatch.replaceUsesOf(state.lastHeader, derolled)
                derolled.addPredecessor(state.lastLatch)
            }
            else -> {
                val predecessors = original.predecessors.map { state[it] }
                derolled.addPredecessors(*predecessors.toTypedArray())
                predecessors.forEach { it.addSuccessor(derolled) }
            }
        }
        if (original is CatchBlock) {
            val throwers = original.throwers.map { state.getOrDefault(it, it) }
            derolled as CatchBlock
            derolled.addThrowers(throwers)
            throwers.forEach { it.addHandler(derolled) }
        }
        for (handle in original.handlers) {
            val derolledHandle = (state.getOrDefault(handle, handle)) as CatchBlock
            derolled.addHandler(derolledHandle)
            derolledHandle.addThrowers(listOf(derolled))
        }

        val successors = original.successors.map {
            state.getOrDefault(it, it)
        }
        derolled.addSuccessors(*successors.toTypedArray())
        successors.forEach { it.addPredecessor(derolled) }
    }

    private fun copyBlockInstructions(state: State, original: BasicBlock) {
        val newBlock = state[original]
        for (inst in original) {
            val updated = inst.update(state.instMappings, inst.location)
            state[inst] = updated
            newBlock += updated

            if (updated is BlockUser) {
                state.blockMappings.forEach { (original, derolled) -> updated.replaceUsesOf(original, derolled) }
            }

            if (updated is PhiInst && original == state.header) {
                val map = mapOf(state[state.latch] to state.lastLatch)
                val previousMap = updated.incomings
                map.forEach { (o, t) -> updated.replaceUsesOf(o, t) }

                if (updated.predecessors.toSet().size == 1) {
                    val actual = previousMap.getValue(state.lastLatch)
                    val mappedActual = if (actual is NullConstant) {
                        val newInst = instructions.getCast(updated.type, actual)
                        newBlock.insertBefore(updated, newInst)
                        newInst
                    } else actual
                    updated.replaceAllUsesWith(mappedActual)
                    state[inst] = mappedActual
                }
            }
        }
    }

    private fun remapBlockPhis(state: State, phiMappings: List<Pair<Instruction, Instruction>>) {
        for ((orig, new) in phiMappings) {
            if (new is PhiInst) {
                val bb = new.parent
                val incomings = new.incomings.filter { it.key in bb.predecessors }
                val phiValue = when (incomings.size) {
                    1 -> {
                        bb -= new
                        incomings.values.first()
                    }
                    else -> {
                        val newPhi = instructions.getPhi(new.type, incomings)
                        bb.replace(new, newPhi)
                        newPhi
                    }
                }
                state[orig] = phiValue
                new.replaceAllUsesWith(phiValue)
            }
        }
    }

    private fun remapMethodPhis(phis: List<PhiInst>, newMappings: Map<PhiInst, Map<BasicBlock, Value>>) {
        for (phi in phis) {
            val newPhi = instructions.getPhi(phi.type, newMappings.getValue(phi)).update(loc = phi.location)

            val bb = phi.parent
            bb.insertBefore(phi, newPhi)
            phi.replaceAllUsesWith(newPhi)
            bb -= phi
        }
    }

    private fun cleanupBody(method: Method, body: List<BasicBlock>) {
        for (block in body) {
            block.predecessors.toTypedArray().forEach { block.removePredecessor(it) }
            block.successors.toTypedArray().forEach { block.removeSuccessor(it) }
            method.remove(block)
        }

        val loopThrowers = method.catchEntries.map { catch ->
            catch to body.filter { it.handlers.contains(catch) }
        }.toMap()

        for ((catch, throwers) in loopThrowers) {
            throwers.forEach {
                catch.removeThrower(it)
                it.removeHandler(catch)
            }
        }
    }
}
