package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexType
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

// TODO: this is work of satan, refactor this damn thing
class CallStack2JavaPrinter(
        val ctx: ExecutionContext,
        override val packageName: String,
        override val klassName: String) : CallStackPrinter {
    private val printedStacks = mutableSetOf<String>()
    private val builder = JavaBuilder(packageName)
    private val klass = builder.run { klass(packageName, klassName) }
    private val resolvedTypes = mutableMapOf<CallStack, CSType>()
    private val actualTypes = mutableMapOf<CallStack, CSType>()
    lateinit var current: JavaBuilder.JavaFunction

    init {
        with(builder) {
            import("java.lang.Throwable")
            import("java.lang.IllegalStateException")
            import("org.junit.Test")

            with(klass) {
                constructor() {}

                method("unknown", listOf(type("T"))) {
                    returnType = type("T")
                    +"throw new IllegalStateException()"
                }
            }
        }
    }

    override fun printCallStack(callStack: CallStack, method: String) {
        printedStacks.clear()
        resolvedTypes.clear()
        actualTypes.clear()
        with(builder) {
            with(klass) {
                current = method(method) {
                    returnType = void
                    annotations += "Test"
                    exceptions += "Throwable"
                }
            }
        }
        resolveTypes(callStack)
        callStack.printAsJava()
    }

    override fun emit() = builder.toString()


    interface CSType {
        fun isSubtype(other: CSType): Boolean
    }

    inner class CSStarProjection : CSType {
        override fun isSubtype(other: CSType) = other is CSStarProjection
        override fun toString() = "*"
    }

    inner class CSClass(val type: Type, val typeParams: List<CSType> = emptyList()) : CSType {
        override fun isSubtype(other: CSType): Boolean = when (other) {
            is CSClass -> when {
                !type.isSubtypeOf(other.type) -> false
                typeParams.size != other.typeParams.size -> false
                else -> typeParams.zip(other.typeParams).all { (a, b) -> a.isSubtype(b) }
            }
            is CSStarProjection -> true
            else -> false
        }

        override fun toString(): String {
            val typeParams = when (typeParams.isNotEmpty()) {
                true -> typeParams.joinToString(", ", prefix = "<", postfix = ">")
                else -> ""
            }
            return type.javaString + typeParams
        }
    }

    inner class CSPrimaryArray(val element: CSType) : CSType {
        override fun isSubtype(other: CSType): Boolean = when (other) {
            is CSPrimaryArray -> element.isSubtype(other.element)
            is CSStarProjection -> true
            else -> false
        }

        override fun toString() = "${element}[]"
    }

    inner class CSArray(val element: CSType) : CSType {
        override fun isSubtype(other: CSType): Boolean = when (other) {
            is CSArray -> when {
                !element.isSubtype(other.element) -> false
                else -> true
            }
            is CSStarProjection -> true
            else -> false
        }

        override fun toString() = "${element}[]"
    }

    val CSType.kfg: Type
        get() = when (this) {
            is CSClass -> type
            is CSArray -> ctx.types.getArrayType(element.kfg)
            is CSPrimaryArray -> ctx.types.getArrayType(element.kfg)
            else -> unreachable { }
        }

    val java.lang.reflect.Type.csType: CSType
        get() = when (this) {
            is java.lang.Class<*> -> when {
                this.isArray -> {
                    val element = this.componentType.csType
                    CSArray(element)
                }
                else -> CSClass(this.kex.getKfgType(ctx.types))
            }
            is ParameterizedType -> {
                val rawType = this.rawType.csType.kfg
                val typeArgs = this.actualTypeArguments.map { it.csType }
                CSClass(rawType, typeArgs)
            }
            is GenericArrayType -> CSArray(this.genericComponentType.csType)
            is TypeVariable<*> -> this.bounds.first().csType
            is WildcardType -> this.upperBounds.first().csType
            else -> TODO()
        }

    private fun CSType.merge(requiredType: CSType): CSType = when {
        this is CSClass && requiredType is CSClass -> {
            val actualKlass = ctx.loader.loadClass(type)
            val requiredKlass = ctx.loader.loadClass(requiredType.type)
            val isAssignable = requiredKlass.isAssignableFrom(actualKlass)
            if (isAssignable && actualKlass.typeParameters.size == requiredKlass.typeParameters.size) {
                CSClass(type, requiredType.typeParams)
            } else if (isAssignable) {
                CSClass(type)
            } else {
                TODO()
            }
        }
        else -> TODO()
    }

    fun CSType?.isAssignable(other: CSType) = this?.let { other.isSubtype(it) } ?: true

    private val KexType.csType get() = this.getKfgType(ctx.types).csType

    private val Type.csType: CSType
        get() = when (this) {
            is ArrayType -> when {
                this.component.isPrimary -> CSPrimaryArray(component.csType)
                else -> CSArray(this.component.csType)
            }
            else -> CSClass(this)
        }

    private fun resolveTypes(callStack: CallStack) {
        callStack.reversed().map { resolveTypes(it) }
    }

    private fun resolveTypes(constructor: Constructor<*>, args: List<CallStack>) {
        val params = constructor.genericParameterTypes
        args.zip(params).forEach { (arg, param) ->
            if (arg !in resolvedTypes) {
                resolvedTypes[arg] = param.csType
                resolveTypes(arg)
            }
        }
    }

    private fun resolveTypes(method: Method, args: List<CallStack>) {
        val params = method.genericParameterTypes.toList()
        args.zip(params).forEach { (arg, param) ->
            if (arg !in resolvedTypes) {
                resolvedTypes[arg] = param.csType
                resolveTypes(arg)
            }
        }
    }

    private fun resolveTypes(call: ApiCall) = when (call) {
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
        is InnerClassConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.constructor.`class`)
            val constructor = reflection.getConstructor(call.constructor, ctx.loader)
            resolveTypes(call.outerObject)
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

    private fun CallStack.printAsJava() {
        if (name in printedStacks) return
        if (this is PrimaryValue<*>) {
            asConstant
            return
        }
        printedStacks += name
        for (call in this) {
            with(current) {
                +printApiCall(this@printAsJava, call)
            }
        }
    }

    private val Class.javaString: String get() = this.type.javaString

    private val Type.javaString: String
        get() = when (this) {
            is NullType -> "null"
            is VoidType -> "void"
            is BoolType -> "boolean"
            ByteType -> "byte"
            ShortType -> "short"
            CharType -> "char"
            IntType -> "int"
            LongType -> "long"
            FloatType -> "float"
            DoubleType -> "double"
            is ArrayType -> "${this.component.javaString}[]"
            else -> {
                val klass = (this as ClassType).`class`
                val canonicalDesc = klass.canonicalDesc
                val splitted = canonicalDesc.split("$")
                ktassert(splitted.isNotEmpty())
                buildString {
                    append(splitted[0])
                    builder.import(this.toString())
                    for (substring in splitted.drop(1)) {
                        append(".$substring")
                        builder.import(this.toString())
                    }
                }
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
        is InnerClassConstructorCall -> printInnerClassConstructor(owner, apiCall)
        is MethodCall -> printMethodCall(owner, apiCall)
        is StaticMethodCall -> printStaticMethodCall(apiCall)
        is NewArray -> printNewArray(owner, apiCall)
        is ArrayWrite -> printArrayWrite(owner, apiCall)
        is FieldSetter -> printFieldSetter(owner, apiCall)
        is StaticFieldSetter -> printStaticFieldSetter(apiCall)
        is EnumValueCreation -> printEnumValueCreation(owner, apiCall)
        is StaticFieldGetter -> printStaticFieldGetter(owner, apiCall)
        is UnknownCall -> printUnknown(owner, apiCall)
        else -> unreachable { log.error("Unknown call") }
    }

    private val <T> PrimaryValue<T>.asConstant: String
        get() = when (val value = value) {
            null -> "null".also {
                actualTypes[this] = CSClass(ctx.types.nullType)
            }
            is Boolean -> "$value".also {
                actualTypes[this] = CSClass(ctx.types.boolType)
            }
            is Byte -> "(byte) ${value}".also {
                actualTypes[this] = CSClass(ctx.types.byteType)
            }
            is Char -> when (value) {
                in 'a'..'z' -> "'$value'"
                in 'A'..'Z' -> "'$value'"
                else -> "(char) ${value.toInt()}"
            }.also {
                actualTypes[this] = CSClass(ctx.types.charType)
            }
            is Short -> "(short) ${value}".also {
                actualTypes[this] = CSClass(ctx.types.shortType)
            }
            is Int -> "$value".also {
                actualTypes[this] = CSClass(ctx.types.intType)
            }
            is Long -> "${value}L".also {
                actualTypes[this] = CSClass(ctx.types.longType)
            }
            is Float -> when {
                value.isNaN() -> "Float.NaN".also {
                    builder.import("java.lang.Float")
                }
                value.isInfinite() && value < 0.0 -> "Float.NEGATIVE_INFINITY".also {
                    builder.import("java.lang.Float")
                }
                value.isInfinite() -> "Float.POSITIVE_INFINITY".also {
                    builder.import("java.lang.Float")
                }
                else -> "${value}F"
            }.also {
                actualTypes[this] = CSClass(ctx.types.floatType)
            }
            is Double -> when {
                value.isNaN() -> "Double.NaN".also {
                    builder.import("java.lang.Double")
                }
                value.isInfinite() && value < 0.0 -> "Double.NEGATIVE_INFINITY".also {
                    builder.import("java.lang.Double")
                }
                value.isInfinite() -> "Double.POSITIVE_INFINITY".also {
                    builder.import("java.lang.Double")
                }
                else -> "$value"
            }.also {
                actualTypes[this] = CSClass(ctx.types.doubleType)
            }
            else -> unreachable { log.error("Unknown primary value ${this}") }
        }

    private fun CallStack.cast(reqType: CSType?): String {
        val actualType = actualTypes[this] ?: return this.stackName
        return when {
            reqType.isAssignable(actualType) -> this.stackName
            else -> "($reqType)${this.stackName}"
        }
    }

    private fun CallStack.forceCastIfNull(reqType: CSType?): String = when (this.stackName) {
        "null" -> "($reqType)${this.stackName}"
        else -> this.cast(reqType)
    }

    private fun printDefaultConstructor(owner: CallStack, call: DefaultConstructorCall): String {
        val actualType = CSClass(call.klass.type)
        return if (resolvedTypes[owner] != null) {
            val rest = resolvedTypes[owner]!!
            val type = actualType.merge(rest)
            actualTypes[owner] = type
            "$type ${owner.name} = new $type()"
        } else {
            actualTypes[owner] = actualType
            "$actualType ${owner.name} = new $actualType()"
        }
    }

    private fun printConstructorCall(owner: CallStack, call: ConstructorCall): String {
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = CSClass(call.klass.type)
        return if (resolvedTypes[owner] != null) {
            val rest = resolvedTypes[owner]!!
            val type = actualType.merge(rest)
            actualTypes[owner] = type
            "$type ${owner.name} = new $type($args)"
        } else {
            actualTypes[owner] = actualType
            "$actualType ${owner.name} = new $actualType($args)"
        }
    }

    private fun innerClassName(innerType: CSType, outerType: CSType, reqOuterType: CSType?): String {
        if (innerType !is CSClass) return innerType.toString()
        if (outerType !is CSClass) return innerType.toString()

        val innerString = (innerType.type as? ClassType)?.`class`?.fullname ?: return innerType.toString()
        val outerString = (outerType.type as? ClassType)?.`class`?.fullname ?: return innerType.toString()
        if (reqOuterType != null && reqOuterType is CSClass) {
            val reqTypeString = (reqOuterType.type as ClassType).`class`.fullname
            if (innerString.startsWith(reqTypeString)) return innerString.removePrefix("$reqTypeString\$").replace('/', '.')
        }
        if (innerString.startsWith(outerString)) return innerString.removePrefix("$outerString\$").replace('/', '.')
        TODO()
    }

    private fun printInnerClassConstructor(owner: CallStack, call: InnerClassConstructorCall): String {
        call.outerObject.printAsJava()
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = CSClass(call.constructor.`class`.type)
        val outerObject = call.outerObject.forceCastIfNull(resolvedTypes[call.outerObject])
        return if (resolvedTypes[owner] != null) {
            val rest = resolvedTypes[owner]!!
            val type = actualType.merge(rest)
            actualTypes[owner] = type
            val innerClassName = innerClassName(type, actualTypes[call.outerObject]!!, resolvedTypes[call.outerObject])
            "$type ${owner.name} = $outerObject.new $innerClassName($args)"
        } else {
            actualTypes[owner] = actualType
            val innerClassName = innerClassName(actualType, actualTypes[call.outerObject]!!, resolvedTypes[call.outerObject])
            "$actualType ${owner.name} = $outerObject.new $innerClassName($args)"
        }
    }

    private fun printExternalConstructorCall(owner: CallStack, call: ExternalConstructorCall): String {
        call.args.forEach { it.printAsJava() }
        val constructor = call.constructor
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = CSClass(constructor.returnType)
        return if (resolvedTypes[owner] != null) {
            val rest = resolvedTypes[owner]!!
            val type = actualType.merge(rest)
            actualTypes[owner] = type
            "$type ${owner.name} = ${constructor.`class`.javaString}.${constructor.name}($args)"
        } else {
            actualTypes[owner] = actualType
            "$actualType ${owner.name} = ${constructor.`class`.javaString}.${constructor.name}($args)"
        }
    }

    private fun printMethodCall(owner: CallStack, call: MethodCall): String {
        call.args.forEach { it.printAsJava() }
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return "${owner.name}.${method.name}($args)"
    }

    private fun printStaticMethodCall(call: StaticMethodCall): String {
        call.args.forEach { it.printAsJava() }
        val klass = call.method.`class`
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return "${klass.javaString}.${method.name}($args)"
    }

    private fun CSType.elementTypeDepth(depth: Int = -1): Pair<Int, CSType> = when (this) {
        is CSArray -> this.element.elementTypeDepth(depth + 1)
        is CSPrimaryArray -> this.element.elementTypeDepth(depth + 1)
        else -> depth to this
    }

    private fun printNewArray(owner: CallStack, call: NewArray): String {
        val actualType = call.asArray.csType
        val (depth, elementType) = actualType.elementTypeDepth()
//        val elementType = when (actualType) {
//            is CSArray -> actualType.element
//            is CSPrimaryArray -> actualType.element
//            else -> TODO()
//        }
        actualTypes[owner] = actualType
        return "$actualType ${owner.name} = new $elementType[${call.length.stackName}]${"[]".repeat(depth)}"
    }

    private fun printArrayWrite(owner: CallStack, call: ArrayWrite): String {
        call.value.printAsJava()
        val requiredType = run {
            when (val resT = resolvedTypes[owner] ?: actualTypes[owner]) {
                is CSArray -> resT.element
                is CSPrimaryArray -> resT.element
                else -> unreachable { }
            }
        }
        return "${owner.name}[${call.index.stackName}] = ${call.value.cast(requiredType)}"
    }

    private fun printFieldSetter(owner: CallStack, call: FieldSetter): String {
        call.value.printAsJava()
        return "${owner.name}.${call.field.name} = ${call.value.stackName}"
    }

    private fun printStaticFieldSetter(call: StaticFieldSetter): String {
        call.value.printAsJava()
        return "${call.klass.javaString}.${call.field.name} = ${call.value.stackName}"
    }

    private fun printEnumValueCreation(owner: CallStack, call: EnumValueCreation): String {
        val actualType = call.klass.type.csType
        actualTypes[owner] = actualType
        return "$actualType ${owner.name} = ${call.klass.javaString}.${call.name}"
    }

    private fun printStaticFieldGetter(owner: CallStack, call: StaticFieldGetter): String {
        val actualType = call.klass.type.csType
        actualTypes[owner] = actualType
        return "$actualType ${owner.name} = ${call.klass.javaString}.${call.name}"
    }

    private fun printUnknown(owner: CallStack, call: UnknownCall): String {
        val type = call.target.type.csType
        actualTypes[owner] = type
        return "$type ${owner.name} = unknown()"
    }
}
