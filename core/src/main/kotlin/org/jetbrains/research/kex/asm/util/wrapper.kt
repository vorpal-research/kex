package org.jetbrains.research.kex.asm.util

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.UnaryOpcode
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.NullType

class StringBuilderWrapper(val name: String) {
    val `class` = CM.getByName("java/lang/StringBuilder")
    val stringBuilderType = TF.getRefType(`class`)
    val builder = IF.getNew(name, stringBuilderType)
    val insns = arrayListOf(builder)

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
    fun append(value: Value): Boolean = when {
        value.type === NullType -> append("null")
        else -> {
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

    fun toStringWrapper(): Instruction {
        val desc = MethodDesc(arrayOf(), TF.getString())
        val toStringMethod = `class`.getMethod("toString", desc)
        val string = IF.getCall(CallOpcode.Virtual(), "${name}Str", toStringMethod, `class`, builder, arrayOf())
        insns.add(string)
        return string
    }
}

class SystemOutWrapper(name: String) {
    val printStream = CM.getByName("java/io/PrintStream")
    val `class` = CM.getByName("java/lang/System")
    val field = `class`.getField("out", TF.getRefType(printStream))
    val sout = IF.getFieldLoad(name, field)
    val insns = arrayListOf(sout)

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

class ReflectionWrapper {
    val classClass = CM.getByName("java/lang/Class")
    val fieldClass = CM.getByName("java/lang/reflect/Field")

    fun getClass(value: Value): Instruction {
        val type = TF.getObject() as ClassType
        val desc = MethodDesc(arrayOf(), TF.getRefType(classClass))
        val getClassMethod = type.`class`.getMethod("getClass", desc)
        return IF.getCall(CallOpcode.Virtual(), "klass", getClassMethod, type.`class`, value, arrayOf())
    }

    fun getDeclaredField(`class`: Value, name: String): Instruction {
        val getFieldMethod = classClass.getMethod("getDeclaredField", MethodDesc(arrayOf(TF.getString()), TF.getRefType(fieldClass)))
        return IF.getCall(CallOpcode.Virtual(), name, getFieldMethod, classClass, `class`, arrayOf(VF.getStringConstant(name)))
    }

    fun setAccessible(field: Value): Instruction {
        val setAccessibleMethod = fieldClass.getMethod("setAccessible", MethodDesc(arrayOf(TF.getBoolType()), TF.getVoidType()))
        return IF.getCall(CallOpcode.Virtual(), "set", setAccessibleMethod, fieldClass, field, arrayOf(VF.getBoolConstant(true)))
    }

    fun getField(field: Value, owner: Value): Instruction {
        val getMethod = fieldClass.getMethod("get", MethodDesc(arrayOf(TF.getObject()), TF.getObject()))
        return IF.getCall(CallOpcode.Virtual(), "get", getMethod, fieldClass, field, arrayOf(owner))
    }
}

class ValuePrinter {
    val reflection = ReflectionWrapper()
    val insns = arrayListOf<Instruction>()
    val system = CM.getByName("java/lang/System")

    private fun getIdentityHashCode(value: Value): Instruction {
        val hashCodeMethod = system.getMethod("identityHashCode", MethodDesc(arrayOf(TF.getObject()), TF.getIntType()))
        val result = IF.getCall(CallOpcode.Static(), "hash", hashCodeMethod, system, arrayOf(value))
        insns.add(result)
        return result
    }

    private fun printField(owner: Value, `class`: Value, field: Field): Instruction {
        val sb = StringBuilderWrapper("sb")
        val fld = reflection.getDeclaredField(`class`, field.name)
        insns.add(fld)
        val setAccessible = reflection.setAccessible(fld)
        insns.add(setAccessible)
        val get = reflection.getField(fld, owner)
        insns.add(get)
        sb.append("${field.name} == ")
        val fldPrinter = ValuePrinter()
        val str = fldPrinter.print(get)
        insns.addAll(fldPrinter.insns)
        sb.append(str)
        val res = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return res
    }

    private fun printClass(value: Value, type: ClassType): Instruction {
        val sb = StringBuilderWrapper("sb")
        sb.append(type.`class`.fullname.replace('/', '.'))
        sb.append("@")
        val hash = getIdentityHashCode(value)
        sb.append(print(hash))
        sb.append("{")
        val `class` = reflection.getClass(value)
        insns.add(`class`)
        val fields = type.`class`.fields.values
        fields.take(1).forEach {
            sb.append(printField(value, `class`, it))
        }
        fields.drop(1).forEach {
            sb.append(", ")
            sb.append(printField(value, `class`, it))
        }
        sb.append("}")
        val res = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return res
    }

    private fun printArray(value: Value, type: ArrayType): Instruction {
        val sb = StringBuilderWrapper("sb")
        sb.append("array")
        sb.append("@")
        val hash = getIdentityHashCode(value)
        sb.append(print(hash))
        sb.append("{")
        sb.append(type.toString().replace('/', '.'))
        sb.append(", ")
        val length = IF.getUnary(UnaryOpcode.LENGTH, value)
        insns.add(length)
        sb.append(print(length))
        sb.append("}")
        val res = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return res
    }

    fun print(value: Value): Instruction {
        val type = value.type
        val sb = StringBuilderWrapper("sb")
        when {
            type.isPrimary() -> sb.append(value)
            type == TF.getString() -> {
                sb.append("\"")
                sb.append(value)
                sb.append("\"")
            }
            type is ArrayType -> sb.append(printArray(value, type))
            type is ClassType -> sb.append(printClass(value, type))
        }
        val result = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return result
    }
}