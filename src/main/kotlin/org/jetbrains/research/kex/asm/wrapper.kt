package org.jetbrains.research.kex.asm

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.NullType

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
        if (value.type is NullType) {
            append("null")
        } else {
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
    }

    fun to_string(): Instruction {
        val desc = MethodDesc(arrayOf(), TF.getString())
        val toStringMethod = `class`.getMethod("toString", desc)
        val string = IF.getCall(CallOpcode.Virtual(), "${builder.name}Str", toStringMethod, `class`, builder, arrayOf())
        insns.add(string)
        return string
    }
}

class SystemOutWrapper(name: String) {
    val printStream = CM.getByName("java/io/PrintStream")
    val `class` = CM.getByName("java/lang/System")
    val field = `class`.getField("out", TF.getRefType(printStream))
    val sout = IF.getFieldLoad(name, field)
    val insns = mutableListOf(sout)

    fun print(string: String) = print(VF.getStringConstant(string))
    fun print(value: Value) {
        val printArg = when {
            value.type.isPrimary() -> value.type
            value.type == TF.getString() -> value.type
            else -> TF.getObject()
        }
        val desc = MethodDesc(arrayOf(printArg), TF.getVoidType())
        val printMethod = `class`.getMethod("print", desc)
        val append = IF.getCall(CallOpcode.Virtual(), printMethod, printStream, sout, arrayOf(value), false)
        insns.add(append)
    }

    fun println(string: String) = println(VF.getStringConstant(string))
    fun println(value: Value) {
        val printArg = when {
            value.type.isPrimary() -> value.type
            value.type == TF.getString() -> value.type
            else -> TF.getObject()
        }
        val desc = MethodDesc(arrayOf(printArg), TF.getVoidType())
        val printMethod = `class`.getMethod("println", desc)
        val append = IF.getCall(CallOpcode.Virtual(), printMethod, printStream, sout, arrayOf(value), false)
        insns.add(append)
    }
}

//class ValuePrinter {
//    val insns = mutableListOf<Instruction>()
//
//    fun print(value: Value): Instruction {
//        val type = value.type
//        val sb = StringBuilderWrapper("sb")
//        sb.append("${value.name} == ")
//        if (type.isPrimary() || type == TF.getString()) {
//            sb.append(value)
//        } else if (type is ArrayType) {
//            sb.append("array ")
//            val cast = IF.getCast(TF.getObject(), value)
//            sb.append(cast)
//            insns.add(cast)
//        } else if (type is ClassType) {
//            sb.append("class ")
//            val cast = IF.getCast(TF.getObject(), value)
//            insns.add(cast)
//            sb.append(cast)
//            sb.append(" {")
//            type.`class`.fields.filter { it.value.isPublic() }.forEach { name, field ->
//                val getField = IF.getFieldLoad(value, field)
//                insns.add(getField)
//                sb.append("field $name ")
//                sb.append(getField)
//            }
//            sb.append("}")
//        }
//        val result = sb.to_string()
//        insns.addAll(sb.insns)
//        return result
//    }
//}