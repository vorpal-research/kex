package org.vorpal.research.kex.asm.transform

import org.vorpal.research.kex.asm.manager.wrapper
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.BodyBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.usageContext
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.graph.DominatorTree
import org.vorpal.research.kthelper.graph.DominatorTreeBuilder
import org.vorpal.research.kthelper.logging.log

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
                domTree = DominatorTreeBuilder(method.body).build()
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
        val newTerminator = inst(cm) { ite(inst.cond, trueBranch, falseBranch) }
        val newTerminatorClone = newTerminator.update(ctx, loc = inst.location)
        newTerminator.replaceAllUsesWith(newTerminatorClone)
        newTerminator.clearUses()

        inst.replaceAllUsesWith(newTerminatorClone)
        parent.replace(inst, newTerminatorClone)
        inst.clearUses()

        val phis = branch.instructions.takeWhile { it is PhiInst }
        for (phi in phis) {
            phi as PhiInst
            val newIncomings = phi.incomings.mapKeys {
                if (it.key == parent) replacement else it.key
            }
            val newPhi = inst(cm) { phi(phi.type, newIncomings) }
            val newPhiClone = newPhi.update(ctx, loc = phi.location)
            newPhi.replaceAllUsesWith(newPhiClone)
            newPhi.clearUses()

            phi.replaceAllUsesWith(newPhiClone)
            branch.replace(phi, newPhiClone)
            phi.clearUses()
        }

        parent.parent.add(replacement)
        replacement.wrapper = branch.wrapper
    }
}