package org.jetbrains.research.kex.reanimator.codegen.kotlingen

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.codegen.CallStackPrinter
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.*

class CallStack2KotlinPrinter : CallStackPrinter {
    private val body = StringBuilder()
    private val printedStacks = mutableSetOf<String>()
    val builder = KtBuilder()
    lateinit var current: KtBuilder.KtFunction

    override fun print(vararg callStacks: CallStack): String {
        with(builder) {
            function("<T> unknown") {
                returnType = type("T")
                +"TODO()"
            }

            function("test") {
                current = this
                returnType = unit
            }
        }
        callStacks.forEach { printCallStack(it) }
        return builder.toString()
    }

    private fun printCallStack(callStack: CallStack) {
        if (callStack.name in printedStacks) return
        printedStacks += callStack.name
        for (call in callStack) {
            with(current) {
                +printApiCall(callStack.name, call)
            }
        }
    }

    private val Class.kotlinString: String get() = this.type.kotlinString

    private val Type.kotlinString: String get() = when (this) {
        is NullType -> "null"
        is VoidType -> "Unit"
        is BoolType -> "Boolean"
        ByteType -> "Byte"
        ShortType -> "Short"
        CharType -> "Char"
        IntType -> "Int"
        LongType -> "Long"
        FloatType -> "Float"
        DoubleType -> "Double"
        is ArrayType -> when (val type = this.component) {
            BoolType -> "BooleanArray"
            ByteType -> "ByteArray"
            ShortType -> "ShortArray"
            CharType -> "CharArray"
            IntType -> "IntArray"
            LongType -> "LongArray"
            FloatType -> "FloatArray"
            DoubleType -> "DoubleArray"
            else -> "Array<${type.kotlinString}>"
        }
        else -> {
            val klass = (this as ClassType).`class`
            val name = klass.canonicalDesc.replace("$", ".")
            builder.import(name)
            klass.name.replace("$", ".")
        }
    }

    private fun printStackName(callStack: CallStack): String = when (callStack) {
        is PrimaryValue<*> -> printConstant(callStack)
        else -> callStack.name
    }

    private fun printApiCall(name: String, apiCall: ApiCall) = when (apiCall) {
        is DefaultConstructorCall -> printDefaultConstructor(name, apiCall)
        is ConstructorCall -> printConstructorCall(name, apiCall)
        is ExternalConstructorCall -> printExternalConstructorCall(name, apiCall)
        is MethodCall -> printMethodCall(name, apiCall)
        is StaticMethodCall -> printStaticMethodCall(apiCall)
        is NewArray -> printNewArray(name, apiCall)
        is ArrayWrite -> printArrayWrite(name, apiCall)
        is FieldSetter -> printFieldSetter(name, apiCall)
        is StaticFieldSetter -> printStaticFieldSetter(apiCall)
        is UnknownCall -> "val $name = unknown()"
        else -> unreachable { log.error("Unknown call") }
    }

    private fun <T> printConstant(constant: PrimaryValue<T>): String = when (val value = constant.value) {
        null -> "null"
        is Boolean -> "$value"
        is Byte -> "${value}.toByte()"
        is Char -> when (value) {
            in 'a'..'z' -> "'${'a' + (value - 'a')}'"
            in 'A'..'Z' -> "'${'A' + (value - 'Z')}'"
            else -> "${value}.toChar()"
        }
        is Short -> "${value}.toShort()"
        is Int -> "$value"
        is Long -> "${value}L"
        is Float -> "${value}F"
        is Double -> "$value"
        else -> unreachable { log.error("Unknown primary value $constant") }
    }

    private fun printDefaultConstructor(name: String, call: DefaultConstructorCall): String =
            "val $name = ${call.klass.kotlinString}()"

    private fun printConstructorCall(name: String, call: ConstructorCall): String {
        call.args.forEach { printCallStack(it) }
        val method = call.constructor
        val args = call.args.withIndex().joinToString(", ") { (index, it) ->
            "${printStackName(it)} as ${method.argTypes[index].kotlinString}"
        }
        return "val $name = ${call.klass.kotlinString}($args)"
    }

    private fun printExternalConstructorCall(name: String, call: ExternalConstructorCall): String {
        call.args.forEach { printCallStack(it) }
        val constructor = call.constructor
        val args = call.args.withIndex().joinToString(", ") { (index, it) ->
            "${printStackName(it)} as ${constructor.argTypes[index].kotlinString}"
        }
        return "val $name = ${constructor.`class`.kotlinString}.${constructor.name}($args)"
    }

    private fun printMethodCall(owner: String, call: MethodCall): String {
        call.args.forEach { printCallStack(it) }
        val method = call.method
        val args = call.args.withIndex().joinToString(", ") { (index, it) ->
            "${printStackName(it)} as ${method.argTypes[index].kotlinString}"
        }
        return "${owner}.${method.name}($args)"
    }

    private fun printStaticMethodCall(call: StaticMethodCall): String {
        call.args.forEach { printCallStack(it) }
        val klass = call.method.`class`
        val method = call.method
        val args = call.args.withIndex().joinToString(", ") { (index, it) ->
            "${printStackName(it)} as ${method.argTypes[index].kotlinString}"
        }
        return "${klass.kotlinString}.${method.name}($args)"
    }

    private fun printNewArray(name: String, call: NewArray): String {
        val newArray = when (val type = call.asArray.component) {
            is ClassType, is ArrayType -> "arrayOfNulls<${type.kotlinString}>"
            else -> call.asArray.kotlinString
        }
        return "val $name = $newArray(${printStackName(call.length)})"
    }

    private fun printArrayWrite(owner: String, call: ArrayWrite): String {
        printCallStack(call.value)
        return "${owner}[${printStackName(call.index)}] = ${printStackName(call.value)}"
    }

    private fun printFieldSetter(owner: String, call: FieldSetter): String {
        printCallStack(call.value)
        return "${owner}.${call.field.name} = ${printStackName(call.value)}"
    }

    private fun printStaticFieldSetter(call: StaticFieldSetter): String {
        printCallStack(call.value)
        return "${call.klass.kotlinString}.${call.field.name} = ${printStackName(call.value)}"
    }
}