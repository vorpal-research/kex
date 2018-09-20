package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

object MethodInliner : MethodVisitor {
    override fun cleanup() {}

    private fun createBlockCopy(block: BasicBlock) = when (block) {
        is BodyBlock -> BodyBlock("${block.name.name}.inlined")
        is CatchBlock -> CatchBlock("${block.name.name}.inlined", block.exception)
        else -> unreachable { log.error("Unknown block type: $block") }
    }

    private fun copyBlock(original: BasicBlock, blocks: MutableMap<BasicBlock, BasicBlock>, values: MutableMap<Value, Value>) {
        val copy = blocks.getOrPut(original) { createBlockCopy(original) }
        for (inst in original) {
            val copiedInst = inst.update(values)
            copy.add(copiedInst)
            values[inst] = copiedInst


            if (copiedInst is BlockUser) {
                blocks.forEach { (original, derolled) -> copiedInst.replaceUsesOf(original, derolled) }
            }
        }

        for (predecessor in original.predecessors) {
            val predMapping = blocks.getOrPut(predecessor) { createBlockCopy(predecessor) }
            copy.addPredecessor(predMapping)
            predMapping.addSuccessor(copy)
        }

        for (successor in original.successors) {
            val succMapping = blocks.getOrPut(successor) { createBlockCopy(successor) }
            copy.addSuccessor(succMapping)
            succMapping.addPredecessor(copy)
            copy.terminator.replaceUsesOf(successor, succMapping)
        }

        original.handlers.forEach { copy.addHandler(blocks.getOrPut(it) { createBlockCopy(it) } as CatchBlock) }
        if (original is CatchBlock) {
            copy as CatchBlock
            copy.addThrowers(original.throwers.map { blocks.getOrPut(it) { createBlockCopy(it) } })
        }
    }

    private fun getValueMapping(inst: CallInst): MutableMap<Value, Value> {
        val valueMapping = hashMapOf<Value, Value>()

        val calledMethod = inst.method
        if (!calledMethod.isStatic) {
            val `this` = VF.getThis(TF.getRefType(calledMethod.`class`))
            valueMapping[`this`] = inst.callee
        }
        for ((index, type) in calledMethod.argTypes.withIndex()) {
            val arg = VF.getArgument(index, calledMethod, type)
            val actualArg = inst.args[index]
            valueMapping[arg] = actualArg
        }

        return valueMapping
    }

    private fun splitBasicBlock(block: BasicBlock, splitPoint: Instruction): Pair<BasicBlock, BasicBlock> {
        val before = when (block) {
            is BodyBlock -> BodyBlock("${block.name.name}.splitted")
            is CatchBlock -> CatchBlock("${block.name.name}.splitted", block.exception)
            else -> unreachable { log.error("Unknown block type: $block") }
        }

        val after = BodyBlock("${block.name.name}.splitted")

        block.predecessors.toTypedArray().forEach {
            it.removeSuccessor(block)
            it.addSuccessor(before)
            before.addPredecessor(it)
            it.replaceUsesOf(block, before)
        }

        var reachedSplitPoint = false
        for (it in block) {
            when {
                it == splitPoint -> reachedSplitPoint = true
                !reachedSplitPoint -> before.add(it.clone())
                else -> after.add(it.clone())
            }
        }

        block.successors.toTypedArray().forEach {
            it.removePredecessor(block)
            it.addPredecessor(after)
            after.addSuccessor(it)
        }

        return before to after
    }

    override fun visitCallInst(inst: CallInst) {
        val method = inst.parent?.parent ?: unreachable { log.error("Instruction without parent method") }
        val inlinedMethod = inst.method
        if (!MethodManager.isInlinable(inlinedMethod)) return

        val entryBlock = inlinedMethod.entry
        val returnBlock = inlinedMethod.basicBlocks.firstOrNull { bb -> bb.any { it is ReturnInst } } ?: return

        val valueMapping = getValueMapping(inst)

        val blockMapping = hashMapOf<BasicBlock, BasicBlock>()
        for (block in inlinedMethod) {
            copyBlock(block, blockMapping, valueMapping)
        }

        val current = inst.parent!!
        val (before, after) = splitBasicBlock(current, inst)

        val entry = blockMapping[entryBlock]!!
        val `return` = blockMapping[returnBlock]!!

        // connect `before` block with method entry
        before.add(IF.getJump(entry))
        before.addSuccessor(entry)
        entry.addPredecessor(before)

        // replace return instruction with jump to original function
        val returnInst = `return`.first { it is ReturnInst } as ReturnInst
        if (returnInst.hasReturnValue) inst.replaceAllUsesWith(returnInst.returnValue)
        val jump = IF.getJump(after)
        returnInst.replaceAllUsesWith(jump)
        `return`.replace(returnInst, jump)

        // connect return block with after` block
        `return`.addSuccessor(after)
        after.addPredecessor(`return`)

        // add inlined method body to method
        method.remove(current)
        method.add(before)
        inlinedMethod.basicBlocks.forEach { method.add(blockMapping.getValue(it)) }
        method.add(after)
    }
}