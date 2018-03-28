package org.jetbrains.research.kex.asm

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode

class SystemOutWrapper(name: String) {
    val printStream = CM.getByName("java/io/PrintStream")
    val `class` = CM.getByName("java/lang/System")
    val field = `class`.getField("out", TF.getRefType(printStream))
    val sout = IF.getFieldLoad(name, field)
    val insns = mutableListOf(sout)

    fun print(string: String) = print(VF.getStringConstant(string))
    fun print(value: Value) {
        val printArg = when {
            value.type.isPrimary() -> value.type.getAsmDesc()
            value.type == TF.getString() -> value.type.getAsmDesc()
            else -> TF.getObject().getAsmDesc()
        }
        val printMethod = `class`.getMethod("print", "($printArg)${TF.getVoidType().getAsmDesc()}")
        val append = IF.getCall(CallOpcode.Virtual(), printMethod, printStream, sout, arrayOf(value))
        insns.add(append)
    }

    fun println(string: String) = println(VF.getStringConstant(string))
    fun println(value: Value) {
        val printArg = when {
            value.type.isPrimary() -> value.type.getAsmDesc()
            value.type == TF.getString() -> value.type.getAsmDesc()
            else -> TF.getObject().getAsmDesc()
        }
        val printMethod = `class`.getMethod("println", "($printArg)${TF.getVoidType().getAsmDesc()}")
        val append = IF.getCall(CallOpcode.Virtual(), printMethod, printStream, sout, arrayOf(value))
        insns.add(append)
    }
}