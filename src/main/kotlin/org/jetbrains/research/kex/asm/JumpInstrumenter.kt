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
        builder.print("method $method, retval = ")
        if (inst.hasReturnValue()) {
            builder.println(inst.getReturnValue())
        } else {
            builder.println("void")
        }

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("method $method, throw ")
        builder.println(inst.getThrowable())

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("bb ${bb.name}, exit")

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBranchInst(inst: BranchInst) {
        val condition = inst.getCond() as CmpInst
        val bb = inst.parent!!

        val sb = StringBuilderWrapper("sb")
        sb.append("${condition.getLhv().name} == ")
        sb.append(condition.getLhv())
        sb.append(", ${condition.getRhv().name} == ")
        sb.append(condition.getRhv())

        val sout = SystemOutWrapper("sout")
        sout.print("bb ${bb.name}, exit, condition: ")
        sout.println(sb.to_string())

        bb.insertBefore(condition, *sb.insns.toTypedArray())
        bb.insertBefore(condition, *sout.insns.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("bb ${bb.name}, exit, switch: ${inst.getKey().name} == ")
        builder.println(inst.getKey())

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("bb ${bb.name}, exit, tableswitch: ${inst.getIndex().name} == ")
        builder.println(inst.getIndex())

        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)

        val builder = SystemOutWrapper("sout")
        builder.println("bb ${bb.name}, enter")

        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
    }

    override fun visit() {
        super.visit()
        val bb = method.getEntry()

        val builder = SystemOutWrapper("sout")
        builder.println("method $method, enter")

        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
        method.slottracker.rerun()
    }
}