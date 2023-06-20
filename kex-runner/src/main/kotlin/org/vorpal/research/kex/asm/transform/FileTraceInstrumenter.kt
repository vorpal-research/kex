package org.vorpal.research.kex.asm.transform

import org.vorpal.research.kex.asm.util.FileOutputStreamWrapper
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.analysis.IRVerifier
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.EmptyUsageContext
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.visitor.MethodVisitor
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class FileTraceInstrumenter(override val cm: ClassManager) : MethodVisitor {
    private val insertedInsts = mutableListOf<Instruction>()
    private lateinit var fos: FileOutputStreamWrapper

    companion object {
        private const val TRACE_POSTFIX = ".trace"
        val TRACE_DIRECTORY = kexConfig.getStringValue("runner", "trace-directory", "./traces")

        private fun generateTraceFileName(method: Method): String {
            val name = "${method.klass.canonicalDesc}.${method.name}"
            return "$name$TRACE_POSTFIX"
        }

        fun getTraceFile(method: Method): File = File(TRACE_DIRECTORY, generateTraceFileName(method))
    }

    private fun instrumentInst(inst: Instruction, instrumenter: (inst: Instruction) -> List<Instruction>) {
        val bb = inst.parent

        val insts = instrumenter(inst)
        insertedInsts += insts
        bb.insertBefore(inst, *insts.toTypedArray())
    }

    override fun cleanup() {
        insertedInsts.clear()
    }

    override fun visitReturnInst(inst: ReturnInst) = instrumentInst(inst) {
        val bb = inst.parent
        val method = bb.method

        buildList {
            addAll(fos.println("exit ${bb.name};"))
            addAll(fos.print("return ${method.prototype.javaString}, "))

            when {
                inst.hasReturnValue -> {
                    addAll(fos.print("${inst.returnValue.name} == "))
                    addAll(fos.printValue(inst.returnValue))
                }

                else -> addAll(fos.print("void"))
            }
            addAll(fos.println(";"))
        }
    }

    override fun visitThrowInst(inst: ThrowInst) = instrumentInst(inst) {
        val bb = inst.parent
        val method = bb.method

        buildList {
            addAll(fos.println("exit ${bb.name};"))
            addAll(fos.print("throw ${method.prototype.javaString}, "))
            addAll(fos.print("${inst.throwable.name} == "))
            addAll(fos.printValue(inst.throwable))
            addAll(fos.println(";"))
        }
    }

    override fun visitJumpInst(inst: JumpInst) = instrumentInst(inst) {
        val bb = inst.parent
        fos.println("exit ${bb.name};")
    }

    override fun visitBranchInst(inst: BranchInst) = instrumentInst(inst) {
        val condition = inst.cond as CmpInst
        val bb = inst.parent

        buildList {
            addAll(fos.print("branch ${bb.name}, ${condition.lhv.name} == "))
            addAll(fos.printValue(condition.lhv))
            addAll(fos.print(", ${condition.rhv.name} == "))
            addAll(fos.printValue(condition.rhv))
            addAll(fos.println(";"))
        }
    }

    override fun visitSwitchInst(inst: SwitchInst) = instrumentInst(inst) {
        val bb = inst.parent

        buildList {
            addAll(fos.print("switch ${bb.name}, ${inst.key.name} == "))
            addAll(fos.printValue(inst.key))
            addAll(fos.println(";"))
        }
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) = instrumentInst(inst) {
        val bb = inst.parent

        buildList {
            addAll(fos.print("tableswitch ${bb.name}, ${inst.index.name} == "))
            addAll(fos.printValue(inst.index))
            addAll(fos.println(";"))
        }
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)

        val insts = fos.println("enter ${bb.name};")
        insertedInsts += insts
        bb.insertBefore(bb.first(), *insts.toTypedArray())
    }

    override fun visit(method: Method) {
        val bb = method.body.entry
        val startInsts = buildList {
            val methodName = method.prototype.javaString
            val traceFileName = getTraceFile(method).absolutePath

            fos = FileOutputStreamWrapper(
                cm,
                EmptyUsageContext,
                "traceFile",
                traceFileName,
                append = true,
                autoFlush = true
            )
            addAll(fos.open())
            addAll(fos.println("enter $methodName;"))

            val args = method.argTypes
            if (!method.isStatic) {
                val thisType = types.getRefType(method.klass)
                val `this` = values.getThis(thisType)
                addAll(fos.print("instance $methodName, this == "))
                addAll(fos.printValue(`this`))
                addAll(fos.println(";"))
            }
            if (args.isNotEmpty()) {
                addAll(fos.print("arguments $methodName"))
                for ((index, type) in args.withIndex()) {
                    val argValue = values.getArgument(index, method, type)
                    addAll(fos.print(", ${argValue.name} == "))
                    addAll(fos.printValue(argValue))
                }
                addAll(fos.println(";"))
            }
        }
        insertedInsts += startInsts

        // visit blocks
        method.body.basicBlocks.toTypedArray().forEach { visitBasicBlock(it) }

        val endInsts = fos.close()
        insertedInsts += endInsts

        bb.insertBefore(bb.first(), *startInsts.toTypedArray())
        method.body.filter { it.any { inst -> inst is ReturnInst } }.forEach {
            it.insertBefore(it.terminator, *endInsts.toTypedArray())
        }
    }

    operator fun invoke(method: Method): List<Instruction> {
        if (!Files.exists(Paths.get(TRACE_DIRECTORY))) {
            Files.createDirectory(Paths.get(TRACE_DIRECTORY))
        }
        visit(method)
        IRVerifier(cm).visit(method)
        return insertedInsts.toList()
    }
}
