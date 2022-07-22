package org.vorpal.research.kex.asm.transform

import org.vorpal.research.kex.asm.util.FileOutputStreamWrapper
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.analysis.IRVerifier
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.EmptyUsageContext
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.collection.buildList
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class TraceInstrumenter(override val cm: ClassManager) : MethodVisitor {
    private val insertedInsts = mutableListOf<Instruction>()
    private lateinit var fos: FileOutputStreamWrapper

    companion object {
        const val TRACE_POSTFIX = ".trace"
        val TRACE_DIRECTORY = kexConfig.getStringValue("runner", "trace-directory", "./traces")

        fun generateTraceFileName(method: Method): String {
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
            +fos.println("exit ${bb.name};")
            +fos.print("return ${method.prototype.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)}, ")

            when {
                inst.hasReturnValue -> {
                    +fos.print("${inst.returnValue.name} == ")
                    +fos.printValue(inst.returnValue)
                }
                else -> +fos.print("void")
            }
            +fos.println(";")
        }
    }

    override fun visitThrowInst(inst: ThrowInst) = instrumentInst(inst) {
        val bb = inst.parent
        val method = bb.method

        buildList {
            +fos.println("exit ${bb.name};")
            +fos.print("throw ${method.prototype.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)}, ")
            +fos.print("${inst.throwable.name} == ")
            +fos.printValue(inst.throwable)
            +fos.println(";")
        }
    }

    override fun visitJumpInst(inst: JumpInst) = instrumentInst(inst) {
        val bb = inst.parent

        buildList {
            +fos.println("exit ${bb.name};")
        }
    }

    override fun visitBranchInst(inst: BranchInst) = instrumentInst(inst) {
        val condition = inst.cond as CmpInst
        val bb = inst.parent

        buildList {
            +fos.print("branch ${bb.name}, ${condition.lhv.name} == ")
            +fos.printValue(condition.lhv)
            +fos.print(", ${condition.rhv.name} == ")
            +fos.printValue(condition.rhv)
            +fos.println(";")
        }
    }

    override fun visitSwitchInst(inst: SwitchInst) = instrumentInst(inst) {
        val bb = inst.parent

        buildList {
            +fos.print("switch ${bb.name}, ${inst.key.name} == ")
            +fos.printValue(inst.key)
            +fos.println(";")
        }
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) = instrumentInst(inst) {
        val bb = inst.parent

        buildList {
            +fos.print("tableswitch ${bb.name}, ${inst.index.name} == ")
            +fos.printValue(inst.index)
            +fos.println(";")
        }
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)

        val insts = buildList<Instruction> {
            +fos.println("enter ${bb.name};")
        }

        insertedInsts += insts
        bb.insertBefore(bb.first(), *insts.toTypedArray())
    }

    override fun visit(method: Method) {
        val bb = method.body.entry
        val startInsts = buildList<Instruction> {
            val methodName = method.prototype.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)
            val traceFileName = getTraceFile(method).absolutePath

            fos = FileOutputStreamWrapper(cm, EmptyUsageContext, "traceFile", traceFileName, append = true, autoFlush = true)
            +fos.open()
            +fos.println("enter $methodName;")

            val args = method.argTypes
            if (!method.isStatic) {
                val thisType = types.getRefType(method.klass)
                val `this` = values.getThis(thisType)
                +fos.print("instance $methodName, this == ")
                +fos.printValue(`this`)
                +fos.println(";")
            }
            if (args.isNotEmpty()) {
                +fos.print("arguments $methodName")
                for ((index, type) in args.withIndex()) {
                    val argValue = values.getArgument(index, method, type)
                    +fos.print(", ${argValue.name} == ")
                    +fos.printValue(argValue)
                }
                +fos.println(";")
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
