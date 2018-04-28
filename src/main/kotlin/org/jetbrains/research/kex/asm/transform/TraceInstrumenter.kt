package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.asm.util.SystemOutWrapper
import org.jetbrains.research.kex.asm.util.ValuePrinter
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor

class TraceInstrumenter(method: Method) : MethodVisitor(method), Loggable {
    companion object {
        const val tracePrefix = "trace"
    }
    val insertedInsts = mutableListOf<Instruction>()

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("${tracePrefix} exit ${bb.name};")
        builder.print("${tracePrefix} return ${method.getPrototype().replace('/', '.')}; ")
        val printer = ValuePrinter()
        if (inst.hasReturnValue()) {
            builder.print("${inst.getReturnValue().name} == ")
            val str = printer.print(inst.getReturnValue())
            builder.print(str)
        } else {
            builder.print("void")
        }
        builder.println(";")

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("${tracePrefix} throw ${method.getPrototype().replace('/', '.')}; ")
        val printer = ValuePrinter()
        builder.print("${inst.getThrowable().name} == ")
        val str = printer.print(inst.getThrowable())
        builder.print(str)
        builder.println(";")

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("${tracePrefix} exit ${bb.name};")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBranchInst(inst: BranchInst) {
        val condition = inst.getCond() as CmpInst
        val bb = inst.parent!!

        val sout = SystemOutWrapper("sout")
        val printer = ValuePrinter()
        sout.print("${tracePrefix} branch ${bb.name}; ${condition.getLhv().name} == ")
        val lhv = printer.print(condition.getLhv())
        val rhv = printer.print(condition.getRhv())
        sout.print(lhv)
        sout.print("; ${condition.getRhv().name} == ")
        sout.print(rhv)
        sout.println(";")

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(sout.insns)
        bb.insertBefore(condition, *printer.insns.toTypedArray())
        bb.insertBefore(condition, *sout.insns.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("${tracePrefix} switch ${bb.name}; ${inst.getKey().name} == ")
        val printer = ValuePrinter()
        val str = printer.print(inst.getKey())
        builder.print(str)
        builder.println(";")

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val bb = inst.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("${tracePrefix} tableswitch ${bb.name}; ${inst.getIndex().name} == ")
        val printer = ValuePrinter()
        val str = printer.print(inst.getIndex())
        builder.print(str)
        builder.println(";")

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)

        val builder = SystemOutWrapper("sout")
        builder.println("${tracePrefix} enter ${bb.name};")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
    }

    override fun visit() {
        super.visit()
        val bb = method.getEntry()
        val methodName = method.getPrototype().replace('/', '.')

        val builder = SystemOutWrapper("sout")
        builder.println("${tracePrefix} enter $methodName;")

        val args = method.desc.args
        val printer = ValuePrinter()
        if (!method.isStatic()) {
            val thisType = TF.getRefType(method.`class`)
            val `this` = VF.getThis(thisType)
            builder.print("${tracePrefix} instance $methodName; this == ")
            val str = printer.print(`this`)
            builder.print(str)
            builder.println(";")
        }
        if (args.isNotEmpty()) {
            builder.print("${tracePrefix} arguments $methodName")
            for ((index, type) in args.withIndex()) {
                val argValue = VF.getArgument(index, method, type)
                builder.print("; ${argValue.name} == ")
                val str = printer.print(argValue)
                builder.print(str)
            }
            builder.println(";")
        }

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(bb.front(), *builder.insns.toTypedArray())
        bb.insertBefore(bb.front(), *printer.insns.toTypedArray())
    }
}