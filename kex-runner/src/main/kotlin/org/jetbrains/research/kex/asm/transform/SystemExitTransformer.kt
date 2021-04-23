package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kthelper.collection.buildList
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.visitor.MethodVisitor

class SystemExitCallException(val code: Int) : Throwable()

class SystemExitTransformer(override val cm: ClassManager) : MethodVisitor {
    val systemClass = cm["java/lang/System"]
    val exitMethod = systemClass.getMethod("exit", "(I)V")

    val exceptionClass = cm["org/jetbrains/research/kex/asm/transform/SystemExitCallException"]
    val exceptionConstructor = exceptionClass.getMethod("<init>", "(I)V")

    override fun cleanup() {}

    override fun visitCallInst(inst: CallInst) {
        val calledMethod = inst.method
        if (calledMethod == exitMethod) {
            val newInsts = buildList<Instruction> {
                val exception = cm.instruction.getNew(exceptionClass.type)
                +exception

                +cm.instruction.getCall(CallOpcode.Special(), exceptionConstructor, exceptionClass,
                        exception, inst.args.toTypedArray(), false)
                +cm.instruction.getThrow(exception)
            }

            inst.parent.insertBefore(inst, *newInsts.toTypedArray())
            inst.parent.remove(inst)
        }
    }
}