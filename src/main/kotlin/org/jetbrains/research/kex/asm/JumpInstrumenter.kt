package org.jetbrains.research.kex.asm

import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor

class JumpInstrumenter(method: Method) : MethodVisitor(method), Loggable {
    val insertedInsts = mutableListOf<Instruction>()

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("instrumented ${bb.name} exit")
        builder.print("instrumented ${method.getPrototype().replace('/', '.')} exit")
        if (inst.hasReturnValue()) {
            builder.print(": ${inst.getReturnValue().name} == ")
            builder.println(inst.getReturnValue())
        }

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("instrumented ${method.getPrototype().replace('/', '.')} throw ")
        builder.println("${inst.getThrowable().name}")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("instrumented ${bb.name} exit")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBranchInst(inst: BranchInst) {
        val condition = inst.getCond() as CmpInst
        val bb = inst.parent!!

        val sb = StringBuilderWrapper("sb")
        sb.append("${condition.getLhv().name} == ")
        sb.append(condition.getLhv())
        sb.append("; ${condition.getRhv().name} == ")
        sb.append(condition.getRhv())

        val sout = SystemOutWrapper("sout")
        sout.print("instrumented ${bb.name} branch: ")
        sout.println(sb.to_string())

        insertedInsts.addAll(sb.insns)
        insertedInsts.addAll(sout.insns)
        bb.insertBefore(condition, *sb.insns.toTypedArray())
        bb.insertBefore(condition, *sout.insns.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("instrumented ${bb.name} switch: ${inst.getKey().name} == ")
        builder.println(inst.getKey())

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("instrumented ${bb.name} tableswitch: ${inst.getIndex().name} == ")
        builder.println(inst.getIndex())

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)

        val builder = SystemOutWrapper("sout")
        builder.println("instrumented ${bb.name} enter")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
    }

    override fun visit() {
        super.visit()
        val bb = method.getEntry()

        val builder = SystemOutWrapper("sout")
        builder.println("instrumented ${method.getPrototype().replace('/', '.')} enter")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
    }
}