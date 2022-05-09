package org.vorpal.research.kex.asm.transform

import org.vorpal.research.kex.ktype.type
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.value.EmptyUsageContext
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.collection.buildList

class SystemExitCallException(val code: Int) : Throwable()

class SystemExitTransformer(override val cm: ClassManager) : MethodVisitor {
    private val systemClass = cm["java/lang/System"]
    private val exitMethod = systemClass.getMethod("exit", "(I)V")

    private val exceptionClass = cm["org/vorpal/research/kex/asm/transform/SystemExitCallException"]
    private val exceptionConstructor = exceptionClass.getMethod("<init>", "(I)V")

    override fun cleanup() {}

    override fun visitCallInst(inst: CallInst) = with(EmptyUsageContext) {
        val calledMethod = inst.method
        if (calledMethod == exitMethod) {
            val newInsts = buildList<Instruction> {
                val exception = inst(cm) { exceptionClass.type.new() }.also { +it }
                +inst(cm) { exceptionConstructor.specialCall(exceptionClass, exception, inst.args.toTypedArray()) }
                +inst(cm) { exception.`throw`() }
            }

            inst.parent.insertBefore(inst, *newInsts.toTypedArray())
            inst.parent.remove(inst)
        }
    }
}