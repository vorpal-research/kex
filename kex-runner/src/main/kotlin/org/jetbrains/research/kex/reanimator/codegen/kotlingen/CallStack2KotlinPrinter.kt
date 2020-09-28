package org.jetbrains.research.kex.reanimator.codegen.kotlingen

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.codegen.CallStackPrinter
import org.jetbrains.research.kfg.type.*

class CallStack2KotlinPrinter : CallStackPrinter {
    private val body = StringBuilder()
    private val printedStacks = mutableSetOf<String>()
    val builder = KtBuilder()
    lateinit var current: KtBuilder.KtFunction

    override fun print(vararg callStacks: CallStack): String {
        with(builder) {
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

    private fun printStackName(callStack: CallStack): String = when (callStack) {
        is PrimaryValue<*> -> printConstant(callStack)
        else -> callStack.name
    }

    private fun printApiCall(name: String, apiCall: ApiCall) = when (apiCall) {
        is DefaultConstructorCall -> printDefaultConstructor(name, apiCall)
        is ConstructorCall -> printConstructorCall(name, apiCall)
        is ExternalConstructorCall -> printExternalConstructorCall(name, apiCall)
        is MethodCall -> printMethodCall(name, apiCall)
        is NewArray -> printNewArray(name, apiCall)
        is ArrayWrite -> printArrayWrite(name, apiCall)
        is FieldSetter -> printFieldSetter(name, apiCall)
        is StaticFieldSetter -> printStaticFieldSetter(apiCall)
        is UnknownCall -> "unknown"
        else -> unreachable { log.error("Unknown call") }
    }

    private fun <T> printConstant(constant: PrimaryValue<T>): String = when (val value = constant.value) {
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
            "val $name = ${call.klass.name}()".also {
                builder.import(call.klass.canonicalDesc)
            }

    private fun printConstructorCall(name: String, call: ConstructorCall): String {
        call.args.forEach { printCallStack(it) }
        return "val $name = ${call.klass.name}(${call.args.joinToString(", ") { printStackName(it) }})".also {
            builder.import(call.klass.canonicalDesc)
        }
    }

    private fun printExternalConstructorCall(name: String, call: ExternalConstructorCall): String {
        call.args.forEach { printCallStack(it) }
        val constructor = call.constructor
        return "val $name = ${constructor.`class`.name}.${constructor.name}(${call.args.joinToString(", ") { printStackName(it) }})".also {
            builder.import(constructor.`class`.canonicalDesc)
        }
    }

    private fun printMethodCall(owner: String, call: MethodCall): String {
        call.args.forEach { printCallStack(it) }
        return "${owner}.${call.method.name}(${call.args.joinToString(", ") { printStackName(it) }})"
    }

    private fun printNewArray(name: String, call: NewArray): String {
        val newArray = when (val type = call.asArray.component) {
            BoolType -> "BooleanArray"
            ByteType -> "ByteArray"
            ShortType -> "ShortArray"
            CharType -> "CharArray"
            IntType -> "IntArray"
            LongType -> "LongArray"
            FloatType -> "FloatArray"
            DoubleType -> "DoubleArray"
            is ArrayType -> "arrayOfNulls<${printArrayType(type.component)}>"
            else -> {
                val klass = (type as ClassType).`class`
                builder.import(klass.canonicalDesc)
                "arrayOfNulls<${klass.name}>"
            }
        }
        return "val $name = $newArray(${printStackName(call.length)})"
    }

    private fun printArrayType(type: Type): String = when (type) {
        BoolType -> "BooleanArray"
        ByteType -> "ByteArray"
        ShortType -> "ShortArray"
        CharType -> "CharArray"
        IntType -> "IntArray"
        LongType -> "LongArray"
        FloatType -> "FloatArray"
        DoubleType -> "DoubleArray"
        is ArrayType -> "Array<${printArrayType(type.component)}>"
        else -> {
            val klass = (type as ClassType).`class`
            builder.import(klass.canonicalDesc)
            "Array<${klass.name}>"
        }
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
        return "${call.klass.name}.${call.field.name} = ${printStackName(call.value)}".also {
            builder.import(call.klass.canonicalDesc)
        }
    }
}