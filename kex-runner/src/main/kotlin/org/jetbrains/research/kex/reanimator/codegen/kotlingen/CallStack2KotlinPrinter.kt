package org.jetbrains.research.kex.reanimator.codegen.kotlingen

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.codegen.CallStackPrinter
import org.jetbrains.research.kex.util.getConstructor
import org.jetbrains.research.kex.util.getMethod
import org.jetbrains.research.kex.util.kex
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.type.Type
import java.lang.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.kotlinFunction
import java.lang.reflect.Type as JType

class CallStack2KotlinPrinter(val ctx: ExecutionContext) : CallStackPrinter {
    private val printedStacks = mutableSetOf<String>()
    val builder = KtBuilder()
    private val resolvedTypes = mutableMapOf<CallStack, CSType>()
    private val actualTypes = mutableMapOf<CallStack, CSType>()
    lateinit var current: KtBuilder.KtFunction

    override fun print(callStack: CallStack): String {
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
        resolveTypes(callStack)
        printCallStack(callStack)
        return builder.toString()
    }


    interface CSType {
        val nullable: Boolean

        fun isSubtype(other: CSType): Boolean
    }

    inner class CSClass(val type: Type, val typeParams: List<CSType> = emptyList(), override val nullable: Boolean = true) : CSType {
        override fun isSubtype(other: CSType): Boolean = when (other) {
            is CSArray -> false
            is CSClass -> when {
                !type.isSubtypeOf(other.type) -> false
                typeParams.size != other.typeParams.size -> false
                typeParams.zip(other.typeParams).any { (a, b) -> !a.isSubtype(b) } -> false
                else -> !(!nullable && other.nullable)
            }
            else -> TODO()
        }

        override fun toString() = type.kotlinString + typeParams.joinToString(", ") + if (nullable) "?" else ""
    }

    inner class CSArray(val element: CSType, override val nullable: Boolean = true) : CSType {
        override fun isSubtype(other: CSType): Boolean = when (other) {
            is CSArray -> when {
                !element.isSubtype(other.element) -> false
                !nullable && other.nullable -> false
                else -> true
            }
            is CSClass -> false
            else -> TODO()
        }

        override fun toString() = "Array<${element}>" + if (nullable) "?" else ""
    }

    fun KType.csType(ctx: ExecutionContext): CSType {
        val type = (this.classifier!! as KClass<*>).java.kex.getKfgType(ctx.types)
        val args = this.arguments.map { it.type!!.csType(ctx) }
        val nullability = this.isMarkedNullable
        return when (type) {
            is ArrayType -> when {
                type.component.isPrimary -> CSArray(type.component.getCsType(), false)
                else -> CSArray(args.first(), nullability)
            }
            else -> CSClass(type, args, nullability)
        }
    }

    fun JType.csType(ctx: ExecutionContext): CSType = when (this) {
        is java.lang.Class<*> -> when {
            this.isArray -> {
                val element = this.componentType.csType(ctx)
                CSArray(element)
            }
            else -> CSClass(this.kex.getKfgType(ctx.types))
        }
        is ParameterizedType -> {
            val type = this.ownerType.csType(ctx)
            val args = this.actualTypeArguments.map { it.csType(ctx) }
            type
        }
        is TypeVariable<*> -> this.bounds.first().csType(ctx)
        is WildcardType -> this.upperBounds.first().csType(ctx)
        else -> TODO()
    }

    fun CSType?.isAssignable(other: CSType) = this?.isSubtype(other) ?: true

    fun Type.getCsType(nullable: Boolean = true): CSType = when (this) {
        is ArrayType -> CSArray(this.component.getCsType(nullable), nullable)
        else -> CSClass(this, nullable = nullable)
    }

    fun resolveTypes(callStack: CallStack) {
        callStack.reversed().map { resolveTypes(it) }
    }

    fun resolveTypes(constructor: Constructor<*>, args: List<CallStack>) =
            when {
                constructor.kotlinFunction != null -> {
                    val params = constructor.kotlinFunction!!.parameters
                    args.zip(params).forEach { (arg, param) ->
                        resolvedTypes[arg] = param.type.csType(ctx)
                    }
                }
                else -> {
                    val params = constructor.genericParameterTypes
                    args.zip(params).forEach { (arg, param) ->
                        resolvedTypes[arg] = param.csType(ctx)
                    }
                }
            }

    fun resolveTypes(method: Method, args: List<CallStack>) =
            when {
                method.kotlinFunction != null -> {
                    val params = method.kotlinFunction!!.parameters.drop(1)
                    args.zip(params).forEach { (arg, param) ->
                        param.type.csType(ctx)
                        resolvedTypes[arg] = param.type.csType(ctx)
                    }
                }
                else -> {
                    val params = method.genericParameterTypes.toList()
                    args.zip(params).forEach { (arg, param) ->
                        resolvedTypes[arg] = param.csType(ctx)
                    }
                }
            }

    fun resolveTypes(call: ApiCall) = when (call) {
        is DefaultConstructorCall -> {
        }
        is ConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.klass)
            val constructor = reflection.getConstructor(call.constructor, ctx.loader)
            resolveTypes(constructor, call.args)
        }
        is ExternalConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.constructor.`class`)
            val constructor = reflection.getMethod(call.constructor, ctx.loader)
            resolveTypes(constructor, call.args)
        }
        is MethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.`class`)
            val method = reflection.getMethod(call.method, ctx.loader)
            resolveTypes(method, call.args)
        }
        is StaticMethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.`class`)
            val method = reflection.getMethod(call.method, ctx.loader)
            resolveTypes(method, call.args)
        }
        else -> {
        }
    }

    private fun printCallStack(callStack: CallStack) {
        if (callStack.name in printedStacks) return
        if (callStack is PrimaryValue<*>) {
            callStack.asConstant
            return
        }
        printedStacks += callStack.name
        for (call in callStack) {
            with(current) {
                +printApiCall(callStack, call)
            }
        }
    }

    private val Class.kotlinString: String get() = this.type.kotlinString

    private val Type.kotlinString: String
        get() = when (this) {
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

    private val CallStack.stackName: String
        get() = when (this) {
            is PrimaryValue<*> -> asConstant
            else -> name
        }

    private fun printApiCall(owner: CallStack, apiCall: ApiCall) = when (apiCall) {
        is DefaultConstructorCall -> printDefaultConstructor(owner, apiCall)
        is ConstructorCall -> printConstructorCall(owner, apiCall)
        is ExternalConstructorCall -> printExternalConstructorCall(owner, apiCall)
        is MethodCall -> printMethodCall(owner, apiCall)
        is StaticMethodCall -> printStaticMethodCall(apiCall)
        is NewArray -> printNewArray(owner, apiCall)
        is ArrayWrite -> printArrayWrite(owner, apiCall)
        is FieldSetter -> printFieldSetter(owner, apiCall)
        is StaticFieldSetter -> printStaticFieldSetter(apiCall)
        is UnknownCall -> "val ${owner.name} = unknown()"
        else -> unreachable { log.error("Unknown call") }
    }

    private val <T> PrimaryValue<T>.asConstant: String
        get() = when (val value = value) {
            null -> "null".also {
                actualTypes[this] = CSClass(ctx.types.nullType)
            }
            is Boolean -> "$value".also {
                actualTypes[this] = CSClass(ctx.types.boolType, nullable = false)
            }
            is Byte -> "${value}.toByte()".also {
                actualTypes[this] = CSClass(ctx.types.byteType, nullable = false)
            }
            is Char -> when (value) {
                in 'a'..'z' -> "'${'a' + (value - 'a')}'"
                in 'A'..'Z' -> "'${'A' + (value - 'Z')}'"
                else -> "${value}.toChar()"
            }.also {
                actualTypes[this] = CSClass(ctx.types.charType, nullable = false)
            }
            is Short -> "${value}.toShort()".also {
                actualTypes[this] = CSClass(ctx.types.shortType, nullable = false)
            }
            is Int -> "$value".also {
                actualTypes[this] = CSClass(ctx.types.intType, nullable = false)
            }
            is Long -> "${value}L".also {
                actualTypes[this] = CSClass(ctx.types.longType, nullable = false)
            }
            is Float -> "${value}F".also {
                actualTypes[this] = CSClass(ctx.types.floatType, nullable = false)
            }
            is Double -> "$value".also {
                actualTypes[this] = CSClass(ctx.types.doubleType, nullable = false)
            }
            else -> unreachable { log.error("Unknown primary value ${this}") }
        }

    private fun CallStack.cast(reqType: CSType?): String {
        val actualType = actualTypes[this]!!
        return when {
            reqType.isAssignable(actualType) -> this.stackName
            else -> "${this.stackName} as $reqType"
        }
    }

    private fun printDefaultConstructor(owner: CallStack, call: DefaultConstructorCall): String {
        val actualType = CSClass(call.klass.type, nullable = false)
        actualTypes[owner] = actualType
        return "val ${owner.name} = $actualType()"
    }

    private fun printConstructorCall(owner: CallStack, call: ConstructorCall): String {
        call.args.forEach { printCallStack(it) }
        val args = call.args.joinToString(", ") {
            it.cast(resolvedTypes[it])
        }
        val actualType = CSClass(call.klass.type, nullable = false)
        actualTypes[owner] = actualType
        return "val ${owner.name} = $actualType($args)"
    }

    private fun printExternalConstructorCall(owner: CallStack, call: ExternalConstructorCall): String {
        call.args.forEach { printCallStack(it) }
        val constructor = call.constructor
        val args = call.args.joinToString(", ") {
            it.cast(resolvedTypes[it])
        }
        val actualType = CSClass(constructor.returnType)
        actualTypes[owner] = actualType
        return "val ${owner.name} = ${constructor.`class`.kotlinString}.${constructor.name}($args)"
    }

    private fun printMethodCall(owner: CallStack, call: MethodCall): String {
        call.args.forEach { printCallStack(it) }
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.cast(resolvedTypes[it])
        }
        return "${owner.name}.${method.name}($args)"
    }

    private fun printStaticMethodCall(call: StaticMethodCall): String {
        call.args.forEach { printCallStack(it) }
        val klass = call.method.`class`
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.cast(resolvedTypes[it])
        }
        return "${klass.kotlinString}.${method.name}($args)"
    }

    private fun printNewArray(owner: CallStack, call: NewArray): String {
        val newArray = when (val type = call.asArray.component) {
            is ClassType, is ArrayType -> {
                actualTypes[owner] = CSArray(type.getCsType(), false)
                "arrayOfNulls<${type.kotlinString}>"
            }
            else -> {
                actualTypes[owner] = call.asArray.getCsType(false)
                call.asArray.kotlinString
            }
        }
        return "val ${owner.name} = $newArray(${call.length.stackName})"
    }

    private fun printArrayWrite(owner: CallStack, call: ArrayWrite): String {
        printCallStack(call.value)
        val requiredType = run {
            val resT = resolvedTypes[owner] ?: actualTypes[owner]
            (resT as CSArray).element
        }
        return "${owner.name}[${call.index.stackName}] = ${call.value.cast(requiredType)}"
    }

    private fun printFieldSetter(owner: CallStack, call: FieldSetter): String {
        printCallStack(call.value)
        return "${owner.name}.${call.field.name} = ${call.value.stackName}"
    }

    private fun printStaticFieldSetter(call: StaticFieldSetter): String {
        printCallStack(call.value)
        return "${call.klass.kotlinString}.${call.field.name} = ${call.value.stackName}"
    }
}