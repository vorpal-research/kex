package org.jetbrains.research.kex.asm.util

import org.jetbrains.research.kthelper.collection.buildList
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.*

interface Wrapper {
    val cm: ClassManager

    val types get() = cm.type
    val values get() = cm.value
    val instructions get() = cm.instruction
}

abstract class PrintStreamWrapper(final override val cm: ClassManager) : Wrapper {
    protected val printStreamClass = cm["java/io/PrintStream"]
    abstract val stream: Value

    abstract fun open(): List<Instruction>

    private fun getPrintArgType(value: Value) = when {
        value.type.isPrimary -> value.type
        value.type == types.stringType -> value.type
        else -> types.objectType
    }

    private fun call(methodName: String, value: Value): List<Instruction> {
        val printArg = getPrintArgType(value)
        val desc = MethodDesc(arrayOf(printArg), types.voidType)
        val printMethod = printStreamClass.getMethod(methodName, desc)
        val append = instructions.getCall(CallOpcode.Virtual(), printMethod, printStreamClass, stream, arrayOf(value), false)
        return listOf(append)
    }

    fun print(string: String) = print(values.getStringConstant(string))
    fun print(value: Value) = call("print", value)

    fun println() = println("")
    fun println(string: String) = println(values.getStringConstant(string))
    fun println(value: Value) = call("println", value)

    fun flush(): List<Instruction> = buildList {
        val desc = MethodDesc(arrayOf(), types.voidType)
        val flushMethod = printStreamClass.getMethod("flush", desc)
        +instructions.getCall(CallOpcode.Virtual(), flushMethod, printStreamClass, stream, arrayOf(), false)
    }

    fun close(): List<Instruction> = buildList {
        val desc = MethodDesc(arrayOf(), types.voidType)
        val closeMethod = printStreamClass.getMethod("close", desc)
        +instructions.getCall(CallOpcode.Virtual(), closeMethod, printStreamClass, stream, arrayOf(), false)
    }
}

class StringBuilderWrapper(override val cm: ClassManager, val name: String) : Wrapper {
    val `class` = cm["java/lang/StringBuilder"]
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
                value.type is CharType -> types.intType
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

abstract class SystemOutputWrapper(cm: ClassManager, name: String, streamType: SystemStream)
    : PrintStreamWrapper(cm) {
    sealed class SystemStream(val name: String) {
        class Output : SystemStream("out")
        class Error : SystemStream("err")

        override fun toString() = name
    }

    final override val types: TypeFactory
        get() = super.types
    final override val instructions: InstructionFactory
        get() = super.instructions

    private val printStream = cm["java/io/PrintStream"]
    val `class` = cm["java/lang/System"]
    val field = `class`.getField(streamType.name, types.getRefType(printStream))
    override val stream = instructions.getFieldLoad(name, field)

    override fun open(): List<Instruction> {
        return listOf(stream)
    }
}

class SystemErrWrapper(cm: ClassManager, name: String) : SystemOutputWrapper(cm, name, SystemStream.Error())
class SystemOutWrapper(cm: ClassManager, name: String) : SystemOutputWrapper(cm, name, SystemStream.Output())

class ReflectionWrapper(override val cm: ClassManager) : Wrapper {
    private val classClass = cm["java/lang/Class"]
    private val fieldClass = cm["java/lang/reflect/Field"]

    fun getClass(value: Value): Instruction {
        val type = types.objectType as ClassType
        val desc = MethodDesc(arrayOf(), types.getRefType(classClass))
        val getClassMethod = type.`class`.getMethod("loadClass", desc)
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
    private val system = cm["java/lang/System"]
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
        sb.append(", 0")
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
            type is NullType -> sb.append("null")
        }
        val result = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return result
    }
}

class FileOutputStreamWrapper(cm: ClassManager, streamName: String,
                              val fileName: String,
                              val append: Boolean = false,
                              val autoFlush: Boolean = false) : PrintStreamWrapper(cm) {
    private val fileClass = cm["java/io/File"]
    private val fileOutputStreamClass = cm["java/io/FileOutputStream"]
    override val stream = instructions.getNew(streamName, types.getRefType(printStreamClass))

    override fun open(): List<Instruction> = buildList {
        val file = instructions.getNew(types.getRefType(fileClass))
        +file

        val constructorDesc = MethodDesc(arrayOf(types.stringType), types.voidType)
        val initMethod = fileClass.getMethod("<init>", constructorDesc)
        val params = arrayOf(values.getStringConstant(fileName))
        +instructions.getCall(CallOpcode.Special(), initMethod, fileClass, file, params, false)

        val createFileDesc = MethodDesc(arrayOf(), types.boolType)
        val createMethod = fileClass.getMethod("createNewFile", createFileDesc)
        +instructions.getCall(CallOpcode.Virtual(), createMethod, fileClass, file, arrayOf(), false)

        val fos = instructions.getNew(types.getRefType(fileOutputStreamClass))
        +fos
        val fosConstructorDesc = MethodDesc(arrayOf(types.getRefType(fileClass), types.boolType), types.voidType)
        val fosInitMethod = fileOutputStreamClass.getMethod("<init>", fosConstructorDesc)
        val fosParams = arrayOf(file, values.getBoolConstant(append))
        +instructions.getCall(CallOpcode.Special(), fosInitMethod, fileOutputStreamClass, fos, fosParams, false)

        +stream
        val outputStreamClass = types.getRefType("java/io/OutputStream")
        val psConstructorDesc = MethodDesc(arrayOf(outputStreamClass, types.boolType), types.voidType)
        val psInitMethod = printStreamClass.getMethod("<init>", psConstructorDesc)
        val psParams = arrayOf(fos, values.getBoolConstant(autoFlush))
        +instructions.getCall(CallOpcode.Special(), psInitMethod, printStreamClass, stream, psParams, false)
    }

    fun printValue(value: Value): List<Instruction> {
        val printer = ValuePrinter(cm)
        val str = printer.print(value)
        return buildList {
            +printer.insns
            +print(str)
        }
    }
}