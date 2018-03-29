package org.jetbrains.research.kex.asm

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

class StringBuilderWrapper(name: String) {
    val `class` = CM.getByName("java/lang/StringBuilder")
    val stringBuilderType = TF.getRefType(`class`)
    val builder = IF.getNew(name, stringBuilderType)
    val insns = mutableListOf(builder)

    init {
        val desc = MethodDesc(arrayOf(), TF.getVoidType())
        val initMethod = `class`.getMethod("<init>", desc)
        val init = IF.getCall(CallOpcode.Special(), initMethod, `class`, builder, arrayOf(), false)
        insns.add(init)
    }

    fun append(vararg values: Value) {
        values.forEach { append(it) }
    }

    fun append(str: String) = append(VF.getStringConstant(str))
    fun append(value: Value) {
        val appendArg = when {
            value.type.isPrimary() -> value.type
            value.type == TF.getString() -> value.type
            else -> TF.getObject()
        }
        val desc = MethodDesc(arrayOf(appendArg), stringBuilderType)
        val appendMethod = `class`.getMethod("append", desc)
        val append = IF.getCall(CallOpcode.Virtual(), appendMethod, `class`, builder, arrayOf(value), false)
        insns.add(append)
    }

    fun to_string(): Instruction {
        val desc = MethodDesc(arrayOf(), TF.getString())
        val toStringMethod = `class`.getMethod("toString", desc)
        val string = IF.getCall(CallOpcode.Virtual(), "${builder.name}Str", toStringMethod, `class`, builder, arrayOf())
        insns.add(string)
        return string
    }
}