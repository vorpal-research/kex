package org.jetbrains.research.kex.asm.util

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.ir.value.instruction.UnaryOpcode
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.NullType
import org.jetbrains.research.kfg.type.TypeFactory

interface Wrapper {
    val cm: ClassManager

    val types get() = cm.type
    val values get() = cm.value
    val instructions get() = cm.instruction
}

class StringBuilderWrapper(override val cm: ClassManager, val name: String) : Wrapper {
    val `class` = cm.getByName("java/lang/StringBuilder")
    private val stringBuilderType = types.getRefType(`class`)
    private val builder = instructions.getNew(name, stringBuilderType)
    val insns = arrayListOf(builder)

    init {
        val desc = MethodDesc(arrayOf(), types.voidType)
        val initMethod = `class`.getMethod("<init>", desc)
        val init = instructions.getCall(CallOpcode.Special(), initMethod, `class`, builder, arrayOf(), false)
        insns.add(init)
    }

    fun append(vararg values: Value) {
        values.forEach { append(it) }
    }

    fun append(str: String) = append(values.getStringConstant(str))
    fun append(value: Value): Boolean = when {
        value.type === NullType -> append("null")
        else -> {
            val appendArg = when {
                value.type.isPrimary -> value.type
                value.type == types.stringType -> value.type
                else -> types.objectType
            }
            val desc = MethodDesc(arrayOf(appendArg), stringBuilderType)
            val appendMethod = `class`.getMethod("append", desc)
            val append = instructions.getCall(CallOpcode.Virtual(), appendMethod, `class`, builder, arrayOf(value), false)
            insns.add(append)
        }
    }

    fun toStringWrapper(): Instruction {
        val desc = MethodDesc(arrayOf(), types.stringType)
        val toStringMethod = `class`.getMethod("toString", desc)
        val string = instructions.getCall(CallOpcode.Virtual(), "${name}Str", toStringMethod, `class`, builder, arrayOf())
        insns.add(string)
        return string
    }
}

abstract class SystemOutputWrapper(final override val cm: ClassManager, name: String, streamType: SystemStream) : Wrapper {
    sealed class SystemStream(val name: String) {
        class Output : SystemStream("out")
        class Error : SystemStream("err")

        override fun toString() = name
    }

    final override val types: TypeFactory
        get() = super.types
    final override val instructions: InstructionFactory
        get() = super.instructions

    private val printStream = cm.getByName("java/io/PrintStream")
    val `class` = cm.getByName("java/lang/System")
    val field = `class`.getField(streamType.name, types.getRefType(printStream))
    private val stream = instructions.getFieldLoad(name, field)
    val insns = arrayListOf(this.stream)

    fun print(string: String) = print(values.getStringConstant(string))
    fun print(value: Value) {
        val printArg = when {
            value.type.isPrimary -> value.type
            value.type == types.stringType -> value.type
            else -> types.objectType
        }

        val desc = MethodDesc(arrayOf(printArg), types.voidType)
        val printMethod = `class`.getMethod("print", desc)
        val append = instructions.getCall(CallOpcode.Virtual(), printMethod, printStream, stream, arrayOf(value), false)
        insns.add(append)
    }

    fun println() = println("")
    fun println(string: String) = println(values.getStringConstant(string))
    fun println(value: Value) {
        val printArg = when {
            value.type.isPrimary -> value.type
            value.type == types.stringType -> value.type
            else -> types.objectType
        }
        val desc = MethodDesc(arrayOf(printArg), types.voidType)
        val printMethod = `class`.getMethod("println", desc)
        val append = instructions.getCall(CallOpcode.Virtual(), printMethod, printStream, stream, arrayOf(value), false)
        insns.add(append)
    }
}

class SystemErrWrapper(cm: ClassManager, name: String) : SystemOutputWrapper(cm, name, SystemStream.Error())
class SystemOutWrapper(cm: ClassManager, name: String) : SystemOutputWrapper(cm, name, SystemStream.Output())

class ReflectionWrapper(override val cm: ClassManager) : Wrapper {
    private val classClass = cm.getByName("java/lang/Class")
    private val fieldClass = cm.getByName("java/lang/reflect/Field")

    fun getClass(value: Value): Instruction {
        val type = types.objectType as ClassType
        val desc = MethodDesc(arrayOf(), types.getRefType(classClass))
        val getClassMethod = type.`class`.getMethod("getClass", desc)
        return instructions.getCall(CallOpcode.Virtual(), "klass", getClassMethod, type.`class`, value, arrayOf())
    }

    fun getDeclaredField(`class`: Value, name: String): Instruction {
        val getFieldMethod = classClass.getMethod("getDeclaredField", MethodDesc(arrayOf(types.stringType), types.getRefType(fieldClass)))
        return instructions.getCall(CallOpcode.Virtual(), name, getFieldMethod, classClass, `class`, arrayOf(values.getStringConstant(name)))
    }

    fun setAccessible(field: Value): Instruction {
        val setAccessibleMethod = fieldClass.getMethod("setAccessible", MethodDesc(arrayOf(types.boolType), types.voidType))
        return instructions.getCall(CallOpcode.Virtual(), "set", setAccessibleMethod, fieldClass, field, arrayOf(values.getBoolConstant(true)))
    }

    fun getField(field: Value, owner: Value): Instruction {
        val getMethod = fieldClass.getMethod("get", MethodDesc(arrayOf(types.objectType), types.objectType))
        return instructions.getCall(CallOpcode.Virtual(), "get", getMethod, fieldClass, field, arrayOf(owner))
    }
}

class ValuePrinter(override val cm: ClassManager) : Wrapper {
    private val reflection = ReflectionWrapper(cm)
    private val system = cm.getByName("java/lang/System")
    val insns = arrayListOf<Instruction>()

    private fun getIdentityHashCode(value: Value): Instruction {
        val hashCodeMethod = system.getMethod("identityHashCode", MethodDesc(arrayOf(types.objectType), types.intType))
        val result = instructions.getCall(CallOpcode.Static(), "hash", hashCodeMethod, system, arrayOf(value))
        insns.add(result)
        return result
    }

    private fun printField(owner: Value, `class`: Value, field: Field): Instruction {
        val sb = StringBuilderWrapper(cm, "sb")
        val fld = reflection.getDeclaredField(`class`, field.name)
        insns.add(fld)
        val setAccessible = reflection.setAccessible(fld)
        insns.add(setAccessible)
        val get = reflection.getField(fld, owner)
        insns.add(get)
        sb.append("${field.name} == ")
        val fldPrinter = ValuePrinter(cm)
        val str = fldPrinter.print(get)
        insns.addAll(fldPrinter.insns)
        sb.append(str)
        val res = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return res
    }

    private fun printClass(value: Value, type: ClassType): Instruction {
        val sb = StringBuilderWrapper(cm, "sb")
        sb.append(type.`class`.canonicalDesc)
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
        val sb = StringBuilderWrapper(cm, "sb")
        sb.append("array")
        sb.append("@")
        val hash = getIdentityHashCode(value)
        sb.append(print(hash))
        sb.append("{")
        sb.append(type.toString().replace('/', '.'))
        sb.append(", ")
        val length = instructions.getUnary(UnaryOpcode.LENGTH, value)
        insns.add(length)
        sb.append(print(length))
        sb.append("}")
        val res = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return res
    }

    fun print(value: Value): Instruction {
        val type = value.type
        val sb = StringBuilderWrapper(cm, "sb")
        when {
            type.isPrimary -> sb.append(value)
            type == types.stringType -> {
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