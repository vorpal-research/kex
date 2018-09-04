package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.asm.util.SystemOutWrapper
import org.jetbrains.research.kex.asm.util.ValuePrinter
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor

object TraceInstrumenter : MethodVisitor {
    const val tracePrefix = "trace"

    val insertedInsts = mutableListOf<Instruction>()

    override fun cleanup() {
        insertedInsts.clear()
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent!!
        val method = bb.parent!!

        val builder = SystemOutWrapper("sout")
        builder.println("$tracePrefix exit ${bb.name};")
        builder.print("$tracePrefix return ${method.prototype.replace('/', '.')}; ")

        val printer = ValuePrinter()
        when {
            inst.hasReturnValue -> {
                builder.print("${inst.returnValue.name} == ")
                val str = printer.print(inst.returnValue)
                builder.print(str)
            }
            else -> builder.print("void")
        }
        builder.println(";")

        insertedInsts.addAll(printer.insns)
        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *printer.insns.toTypedArray())
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val bb = inst.parent!!
        val method = bb.parent!!

        val builder = SystemOutWrapper("sout")
        builder.print("$tracePrefix throw ${method.prototype.replace('/', '.')}; ")

        val printer = ValuePrinter()
        builder.print("${inst.throwable.name} == ")
        val str = printer.print(inst.throwable)
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
        builder.println("$tracePrefix exit ${bb.name};")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(inst, *builder.insns.toTypedArray())
    }

    override fun visitBranchInst(inst: BranchInst) {
        val condition = inst.cond as CmpInst
        val bb = inst.parent!!

        val sout = SystemOutWrapper("sout")
        val printer = ValuePrinter()
        sout.print("$tracePrefix branch ${bb.name}; ${condition.lhv.name} == ")
        val lhv = printer.print(condition.lhv)
        val rhv = printer.print(condition.rhv)
        sout.print(lhv)
        sout.print("; ${condition.rhv.name} == ")
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
        builder.print("$tracePrefix switch ${bb.name}; ${inst.key.name} == ")
        val printer = ValuePrinter()
        val str = printer.print(inst.key)
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
        builder.print("$tracePrefix tableswitch ${bb.name}; ${inst.index.name} == ")
        val printer = ValuePrinter()
        val str = printer.print(inst.index)
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
        builder.println("$tracePrefix enter ${bb.name};")

        insertedInsts.addAll(builder.insns)
        bb.insertBefore(bb.first(), *builder.insns.toTypedArray())
    }

    override fun visit(method: Method) {
        super.visit(method)
        val bb = method.entry
        val methodName = method.prototype.replace('/', '.')

        val builder = SystemOutWrapper("sout")
        builder.println("$tracePrefix enter $methodName;")

        val args = method.desc.args
        val printer = ValuePrinter()
        if (!method.isStatic) {
            val thisType = TF.getRefType(method.`class`)
            val `this` = VF.getThis(thisType)
            builder.print("$tracePrefix instance $methodName; this == ")
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
        bb.insertBefore(bb.first(), *builder.insns.toTypedArray())
        bb.insertBefore(bb.first(), *printer.insns.toTypedArray())
    }

    operator fun invoke(method: Method): List<Instruction> {
        visit(method)
        return insertedInsts.toList()
    }
}