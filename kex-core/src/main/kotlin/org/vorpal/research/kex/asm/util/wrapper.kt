@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.vorpal.research.kex.asm.util

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InstructionBuilder
import org.vorpal.research.kfg.ir.value.instruction.InstructionFactory
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.CharType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.NullType
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kfg.type.stringType

interface Wrapper : InstructionBuilder

abstract class PrintStreamWrapper(final override val cm: ClassManager, override val ctx: UsageContext) : Wrapper {
    protected val printStreamClass = cm["java/io/PrintStream"]
    abstract val stream: Value

    abstract fun open(): List<Instruction>

    private fun getPrintArgType(value: Value) = when {
        value.type.isPrimitive -> value.type
        value.type == types.stringType -> value.type
        else -> types.objectType
    }

    private fun call(methodName: String, value: Value): List<Instruction> {
        val printArg = getPrintArgType(value)
        val printMethod = printStreamClass.getMethod(methodName, types.voidType, printArg)
        val append = printMethod.virtualCall(printStreamClass, stream, listOf(value))
        return listOf(append)
    }

    fun print(string: String) = print(values.getString(string))
    fun print(value: Value) = call("print", value)

    fun println() = println("")
    fun println(string: String) = println(values.getString(string))
    fun println(value: Value) = call("println", value)

    fun flush(): List<Instruction> = buildList {
        val flushMethod = printStreamClass.getMethod("flush", types.voidType)
        add(flushMethod.virtualCall(printStreamClass, stream, listOf()))
    }

    fun close(): List<Instruction> = buildList {
        val closeMethod = printStreamClass.getMethod("close", types.voidType)
        add(closeMethod.virtualCall(printStreamClass, stream, listOf()))
    }
}

class StringBuilderWrapper(override val cm: ClassManager, override val ctx: UsageContext, val name: String) : Wrapper {
    val klass = cm["java/lang/StringBuilder"]
    private val stringBuilderType = types.getRefType(klass)
    private val builder = stringBuilderType.new(name)
    val insns = arrayListOf(builder)

    init {
        val initMethod = klass.getMethod("<init>", types.voidType)
        insns.add(initMethod.specialCall(klass, builder, listOf()))
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
                value.type.isPrimitive -> value.type
                value.type == types.stringType -> value.type
                else -> types.objectType
            }
            val appendMethod = klass.getMethod("append", stringBuilderType, appendArg)
            insns.add(appendMethod.virtualCall(klass, builder, listOf(value)))
        }
    }

    fun toStringWrapper(): Instruction {
        val toStringMethod = klass.getMethod("toString", types.stringType)
        val string = toStringMethod.virtualCall(klass, "${name}Str", builder, listOf())
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
    override val stream by lazy { field.load(name) }

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
        val getClassMethod = type.klass.getMethod("loadClass", types.getRefType(classClass))
        return getClassMethod.virtualCall(type.klass, "klass", value, listOf())
    }

    fun getDeclaredField(klass: Value, name: String): Instruction {
        val getFieldMethod = classClass.getMethod(
            "getDeclaredField",
            types.getRefType(fieldClass), types.stringType
        )
        return getFieldMethod.virtualCall(classClass, name, klass, listOf(values.getString(name)))
    }

    fun setAccessible(field: Value): Instruction {
        val setAccessibleMethod =
            fieldClass.getMethod("setAccessible", types.voidType, types.boolType)
        return setAccessibleMethod.virtualCall(fieldClass, "set", field, listOf(values.trueConstant))
    }

    fun getField(field: Value, owner: Value): Instruction {
        val getMethod = fieldClass.getMethod("get", types.objectType, types.objectType)
        return getMethod.virtualCall(fieldClass, "get", field, listOf(owner))
    }
}

class ValuePrinter(override val cm: ClassManager, override val ctx: UsageContext) : Wrapper {
    private val reflection = ReflectionWrapper(cm, ctx)
    private val system = cm["java/lang/System"]
    val insns = arrayListOf<Instruction>()

    private fun getIdentityHashCode(value: Value): Instruction {
        val hashCodeMethod = system.getMethod("identityHashCode", types.intType, types.objectType)
        val result = hashCodeMethod.staticCall(system, listOf(value))
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
            type.isPrimitive -> sb.append(value)
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
        add(file)

        val initMethod = fileClass.getMethod("<init>", types.voidType, types.stringType)
        add(initMethod.specialCall(fileClass, file, listOf(values.getString(fileName))))

        val createMethod = fileClass.getMethod("createNewFile", types.boolType)
        add(createMethod.virtualCall(fileClass, file, listOf()))

        val fos = fileOutputStreamClass.new()
        add(fos)
        val fosInitMethod = fileOutputStreamClass.getMethod(
            "<init>",
            types.voidType, types.getRefType(fileClass), types.boolType
        )
        add(fosInitMethod.specialCall(fileOutputStreamClass, fos, listOf(file, values.getBool(append))))

        add(stream)
        val outputStreamClass = types.getRefType("java/io/OutputStream")
        val psInitMethod = printStreamClass.getMethod("<init>", types.voidType, outputStreamClass, types.boolType)
        add(psInitMethod.specialCall(printStreamClass, stream, listOf(fos, values.getBool(autoFlush))))
    }

    fun printValue(value: Value): List<Instruction> {
        val printer = ValuePrinter(cm, ctx)
        val str = printer.print(value)
        return buildList {
            addAll(printer.insns)
            addAll(print(str))
        }
    }
}
