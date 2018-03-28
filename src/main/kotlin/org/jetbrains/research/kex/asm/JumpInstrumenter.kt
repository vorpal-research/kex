package org.jetbrains.research.kex.asm

import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor


class JumpInstrumenter(method: Method) : MethodVisitor(method), Loggable {
    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("Leaving method $method")

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("Leaving method $method, throw ")
        builder.println(inst.getThrowable())

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("Leaving ${bb.name}")

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBranchInst(inst: BranchInst) {
        val condition = inst.getCond() as CmpInst
        val bb = inst.parent!!

        val sb = StringBuilderWrapper("sb")
        sb.append(condition.getLhv())
        sb.append(condition.opcode.name)
        sb.append(condition.getRhv())

        val sout = SystemOutWrapper("sout")
        sout.print("Leaving ${bb.name}, condition: ")
        sout.println(sb.to_string())

        bb.insertBefore(condition, *sb.insns.toTypedArray())
        bb.insertBefore(condition, *sout.insns.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("Leaving ${bb.name}")

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("Leaving ${bb.name}")

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visit() {
        super.visit()
        val bb = method.getEntry()

        val builder = SystemOutWrapper("sout")
        builder.println("Entering method $method")

        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)

        val builder = SystemOutWrapper("sout")
        builder.println("Entering bb ${bb.name}")

        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
    }
}