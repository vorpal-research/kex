package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.asm.manager.wrapper
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.instruction.BranchInst
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.ir.value.usageContext
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.KtException
import org.jetbrains.research.kthelper.algorithm.DominatorTree
import org.jetbrains.research.kthelper.algorithm.DominatorTreeBuilder
import org.jetbrains.research.kthelper.logging.log

class BranchAdapter(
    override val cm: ClassManager,
) : MethodVisitor {
    private lateinit var domTree: DominatorTree<BasicBlock>
    private lateinit var ctx: UsageContext

    override fun cleanup() {}

    override fun visit(method: Method) = method.usageContext.use {
        ctx = it
        try {
            if (!method.isAbstract && !method.isNative && method.hasBody) {
                domTree = DominatorTreeBuilder(method).build()
            }
            super.visit(method)
        } catch (e: KtException) {
            log.error("Unexpected exception during branch adapting for $method: $e")
        }
    }

    override fun visitBranchInst(inst: BranchInst) {
        super.visitBranchInst(inst)
        val parent = inst.parent
        val trueBranch = inst.trueSuccessor
        val falseBranch = inst.falseSuccessor
        val trueBranchIdom = domTree[trueBranch]?.idom?.value
        val falseBranchIdom = domTree[falseBranch]?.idom?.value
        if (trueBranch.predecessors.size > 1 && trueBranchIdom == parent) {
            replaceBlock(inst, parent, trueBranch)
        }
        if (falseBranch.predecessors.size > 1 && falseBranchIdom == parent) {
            replaceBlock(inst, parent, falseBranch)
        }
    }

    private fun replaceBlock(inst: BranchInst, parent: BasicBlock, branch: BasicBlock) = with(ctx) {
        val replacement = BodyBlock("branch")

        branch.removePredecessor(parent)
        parent.removeSuccessor(branch)

        replacement += inst(cm) { goto(branch) }
        replacement.addSuccessor(branch)
        branch.addPredecessor(replacement)

        parent.addSuccessor(replacement)
        replacement.addPredecessor(parent)

        val (trueBranch, falseBranch) = when (inst.trueSuccessor) {
            branch -> replacement to inst.falseSuccessor
            else -> inst.trueSuccessor to replacement
        }
        val newTerminator = inst(cm) { ite(inst.cond, trueBranch, falseBranch).update(ctx, loc = inst.location) }
        inst.replaceAllUsesWith(newTerminator)
        parent.replace(inst, newTerminator)
        inst.clearUses()

        val phis = branch.instructions.takeWhile { it is PhiInst }
        for (phi in phis) {
            phi as PhiInst
            val newIncomings = phi.incomings.mapKeys {
                if (it.key == parent) replacement else it.key
            }
            val newPhi = inst(cm) { phi(phi.type, newIncomings).update(ctx, loc = phi.location) }
            phi.replaceAllUsesWith(newPhi)
            branch.replace(phi, newPhi)
            phi.clearUses()
        }

        parent.parent.add(replacement)
        replacement.wrapper = branch.wrapper
    }
}