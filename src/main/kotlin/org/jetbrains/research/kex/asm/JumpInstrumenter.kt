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
        val printer = ValuePrinter()
        if (inst.hasReturnValue()) {
            builder.print(": ${inst.getReturnValue().name} == ")
            val str = printer.print(inst.getReturnValue())
            builder.println(str)
        }

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
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

        val sout = SystemOutWrapper("sout")
        val printer = ValuePrinter()
        sout.print("instrumented ${bb.name} branch: ${condition.getLhv().name} == ")
        val lhv = printer.print(condition.getLhv())
        val rhv = printer.print(condition.getRhv())
        sout.print(lhv)
        sout.print("; ${condition.getRhv().name} == ")
        sout.println(rhv)

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(sout.insns)
        bb.insertBefore(condition, *printer.insns.toTypedArray())
        bb.insertBefore(condition, *sout.insns.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("instrumented ${bb.name} switch: ${inst.getKey().name} == ")
        val printer = ValuePrinter()
        val str = printer.print(inst.getKey())
        builder.println(str)

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("instrumented ${bb.name} tableswitch: ${inst.getIndex().name} == ")
        val printer = ValuePrinter()
        val str = printer.print(inst.getIndex())
        builder.println(str)

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
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