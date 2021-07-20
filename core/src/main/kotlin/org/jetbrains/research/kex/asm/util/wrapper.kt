package org.jetbrains.research.kex.asm.util

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.InstructionBuilder
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kthelper.collection.buildList

interface Wrapper : InstructionBuilder

abstract class PrintStreamWrapper(final override val cm: ClassManager, override val ctx: UsageContext) : Wrapper {
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
        val append = printMethod.virtualCall(printStreamClass, stream, arrayOf(value))
        return listOf(append)
    }

    fun print(string: String) = print(values.getString(string))
    fun print(value: Value) = call("print", value)

    fun println() = println("")
    fun println(string: String) = println(values.getString(string))
    fun println(value: Value) = call("println", value)

    fun flush(): List<Instruction> = buildList {
        val desc = MethodDesc(arrayOf(), types.voidType)
        val flushMethod = printStreamClass.getMethod("flush", desc)
        +flushMethod.virtualCall(printStreamClass, stream, arrayOf())
    }

    fun close(): List<Instruction> = buildList {
        val desc = MethodDesc(arrayOf(), types.voidType)
        val closeMethod = printStreamClass.getMethod("close", desc)
        +closeMethod.virtualCall(printStreamClass, stream, arrayOf())
    }
}

class StringBuilderWrapper(override val cm: ClassManager, override val ctx: UsageContext, val name: String) : Wrapper {
    val klass = cm["java/lang/StringBuilder"]
    private val stringBuilderType = types.getRefType(klass)
    private val builder = stringBuilderType.new(name)
    val insns = arrayListOf(builder)

    init {
        val desc = MethodDesc(arrayOf(), types.voidType)
        val initMethod = klass.getMethod("<init>", desc)
        insns.add(initMethod.specialCall(klass, builder, arrayOf()))
    }

    fun append(vararg values: Value) {
        values.forEach { append(it) }
    }

    fun append(str: String) = append(values.getString(str))
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
            val appendMethod = klass.getMethod("append", desc)
            insns.add(appendMethod.virtualCall(klass, builder, arrayOf(value)))
        }
    }

    fun toStringWrapper(): Instruction {
        val desc = MethodDesc(arrayOf(), types.stringType)
        val toStringMethod = klass.getMethod("toString", desc)
        val string = toStringMethod.virtualCall(klass, "${name}Str", builder, arrayOf())
        insns.add(string)
        return string
    }
}

abstract class SystemOutputWrapper(cm: ClassManager, ctx: UsageContext, name: String, streamType: SystemStream) :
    PrintStreamWrapper(cm, ctx) {
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
    val klass = cm["java/lang/System"]
    val field = klass.getField(streamType.name, types.getRefType(printStream))
    override val stream = field.load(name)

    override fun open(): List<Instruction> {
        return listOf(stream)
    }
}

class SystemErrWrapper(cm: ClassManager, ctx: UsageContext, name: String) :
    SystemOutputWrapper(cm, ctx, name, SystemStream.Error())

class SystemOutWrapper(cm: ClassManager, ctx: UsageContext, name: String) :
    SystemOutputWrapper(cm, ctx, name, SystemStream.Output())

class ReflectionWrapper(override val cm: ClassManager, override val ctx: UsageContext) : Wrapper {
    private val classClass = cm["java/lang/Class"]
    private val fieldClass = cm["java/lang/reflect/Field"]

    fun getClass(value: Value): Instruction {
        val type = types.objectType as ClassType
        val desc = MethodDesc(arrayOf(), types.getRefType(classClass))
        val getClassMethod = type.klass.getMethod("loadClass", desc)
        return getClassMethod.virtualCall(type.klass, "klass", value, arrayOf())
    }

    fun getDeclaredField(klass: Value, name: String): Instruction {
        val getFieldMethod = classClass.getMethod(
            "getDeclaredField",
            MethodDesc(arrayOf(types.stringType), types.getRefType(fieldClass))
        )
        return getFieldMethod.virtualCall(classClass, name, klass, arrayOf(values.getString(name)))
    }

    fun setAccessible(field: Value): Instruction {
        val setAccessibleMethod =
            fieldClass.getMethod("setAccessible", MethodDesc(arrayOf(types.boolType), types.voidType))
        return setAccessibleMethod.virtualCall(fieldClass, "set", field, arrayOf(values.trueConstant))
    }

    fun getField(field: Value, owner: Value): Instruction {
        val getMethod = fieldClass.getMethod("get", MethodDesc(arrayOf(types.objectType), types.objectType))
        return getMethod.virtualCall(fieldClass, "get", field, arrayOf(owner))
    }
}

class ValuePrinter(override val cm: ClassManager, override val ctx: UsageContext) : Wrapper {
    private val reflection = ReflectionWrapper(cm, ctx)
    private val system = cm["java/lang/System"]
    val insns = arrayListOf<Instruction>()

    private fun getIdentityHashCode(value: Value): Instruction {
        val hashCodeMethod = system.getMethod("identityHashCode", MethodDesc(arrayOf(types.objectType), types.intType))
        val result = hashCodeMethod.staticCall(system, arrayOf(value))
        insns.add(result)
        return result
    }

    private fun printField(owner: Value, klass: Value, field: Field): Instruction {
        val sb = StringBuilderWrapper(cm, ctx, "sb")
        val fld = reflection.getDeclaredField(klass, field.name)
        insns.add(fld)
        val setAccessible = reflection.setAccessible(fld)
        insns.add(setAccessible)
        val get = reflection.getField(fld, owner)
        insns.add(get)
        sb.append("${field.name} == ")
        val fldPrinter = ValuePrinter(cm, ctx)
        val str = fldPrinter.print(get)
        insns.addAll(fldPrinter.insns)
        sb.append(str)
        val res = sb.toStringWrapper()
        insns.addAll(sb.insns)
        return res
    }

    private fun printClass(value: Value, type: ClassType): Instruction {
        val sb = StringBuilderWrapper(cm, ctx, "sb")
        sb.append(type.klass.canonicalDesc)
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
        val sb = StringBuilderWrapper(cm, ctx, "sb")
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
        val sb = StringBuilderWrapper(cm, ctx, "sb")
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

class FileOutputStreamWrapper(
    cm: ClassManager,
    ctx: UsageContext,
    streamName: String,
    val fileName: String,
    val append: Boolean = false,
    val autoFlush: Boolean = false
) : PrintStreamWrapper(cm, ctx) {
    private val fileClass = cm["java/io/File"]
    private val fileOutputStreamClass = cm["java/io/FileOutputStream"]
    override val stream = printStreamClass.new(streamName)

    override fun open(): List<Instruction> = buildList {
        val file = fileClass.new()
        +file

        val constructorDesc = MethodDesc(arrayOf(types.stringType), types.voidType)
        val initMethod = fileClass.getMethod("<init>", constructorDesc)
        val params = arrayOf(values.getString(fileName))
        +initMethod.specialCall(fileClass, file, params)

        val createFileDesc = MethodDesc(arrayOf(), types.boolType)
        val createMethod = fileClass.getMethod("createNewFile", createFileDesc)
        +createMethod.virtualCall(fileClass, file, arrayOf())

        val fos = fileOutputStreamClass.new()
        +fos
        val fosConstructorDesc = MethodDesc(arrayOf(types.getRefType(fileClass), types.boolType), types.voidType)
        val fosInitMethod = fileOutputStreamClass.getMethod("<init>", fosConstructorDesc)
        val fosParams = arrayOf(file, values.getBool(append))
        +fosInitMethod.specialCall(fileOutputStreamClass, fos, fosParams)

        +stream
        val outputStreamClass = types.getRefType("java/io/OutputStream")
        val psConstructorDesc = MethodDesc(arrayOf(outputStreamClass, types.boolType), types.voidType)
        val psInitMethod = printStreamClass.getMethod("<init>", psConstructorDesc)
        val psParams = arrayOf(fos, values.getBool(autoFlush))
        +psInitMethod.specialCall(printStreamClass, stream, psParams)
    }

    fun printValue(value: Value): List<Instruction> {
        val printer = ValuePrinter(cm, ctx)
        val str = printer.print(value)
        return buildList {
            +printer.insns
            +print(str)
        }
    }
}