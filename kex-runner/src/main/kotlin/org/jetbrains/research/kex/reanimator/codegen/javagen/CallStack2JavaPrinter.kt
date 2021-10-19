package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.codegen.CallStackPrinter
import org.jetbrains.research.kex.util.getConstructor
import org.jetbrains.research.kex.util.getMethod
import org.jetbrains.research.kex.util.kex
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import java.lang.reflect.*

// TODO: this is work of satan, refactor this damn thing
open class CallStack2JavaPrinter(
    val ctx: ExecutionContext,
    final override val packageName: String,
    final override val klassName: String
) : CallStackPrinter {
    protected val printedStacks = mutableSetOf<String>()
    protected val builder = JavaBuilder(packageName)
    protected val klass = builder.run { klass(packageName, klassName) }
    protected val resolvedTypes = mutableMapOf<CallStack, CSType>()
    protected val actualTypes = mutableMapOf<CallStack, CSType>()
    lateinit var current: JavaBuilder.JavaFunction
    protected var staticCounter = 0

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

    private fun buildCallStack(
        method: org.jetbrains.research.kfg.ir.Method, callStacks: Parameters<CallStack>
    ): CallStack = when {
        method.isStatic -> StaticMethodCall(method, callStacks.arguments).wrap("static${staticCounter++}")
        method.isConstructor -> callStacks.instance!!
        else -> {
            val instance = callStacks.instance!!.clone()
            instance.stack += MethodCall(method, callStacks.arguments)
            instance
        }
    }

    protected open fun cleanup() {
        printedStacks.clear()
        resolvedTypes.clear()
        actualTypes.clear()
    }

    override fun printCallStack(
        testName: String,
        method: org.jetbrains.research.kfg.ir.Method,
        callStacks: Parameters<CallStack>
    ) {
        cleanup()
        val callStack = buildCallStack(method, callStacks)
        with(builder) {
            with(klass) {
                current = method(testName) {
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
                typeParams.isEmpty() && other.typeParams.isNotEmpty() -> true
                typeParams.size != other.typeParams.size -> false
                else -> typeParams.zip(other.typeParams).all { (a, b) -> a.isSubtype(b) }
            }
            is CSStarProjection -> true
            else -> other.kfg == ctx.types.objectType
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
            else -> other.kfg == ctx.types.objectType
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

    private val java.lang.reflect.Type.csType: CSType
        get() = when (this) {
            is java.lang.Class<*> -> when {
                this.isArray -> {
                    val element = this.componentType.csType
                    if (this.componentType.isPrimitive) CSPrimaryArray(element)
                    else CSArray(element)
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

    private fun CSType?.isAssignable(other: CSType) = this?.let { other.isSubtype(it) } ?: true

    private val KexType.csType get() = this.getKfgType(ctx.types).csType

    protected val Type.csType: CSType
        get() = when (this) {
            is ArrayType -> when {
                this.component.isPrimary -> CSPrimaryArray(component.csType)
                else -> CSArray(this.component.csType)
            }
            else -> CSClass(this)
        }

    protected fun resolveTypes(callStack: CallStack) {
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
            val reflection = ctx.loader.loadClass(call.constructor.klass)
            val constructor = reflection.getConstructor(call.constructor, ctx.loader)
            resolveTypes(constructor, call.args)
        }
        is ExternalConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.constructor.klass)
            val constructor = reflection.getMethod(call.constructor, ctx.loader)
            resolveTypes(constructor, call.args)
        }
        is InnerClassConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.constructor.klass)
            val constructor = reflection.getConstructor(call.constructor, ctx.loader)
            resolveTypes(call.outerObject)
            resolveTypes(constructor, call.args)
        }
        is MethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.klass)
            val method = reflection.getMethod(call.method, ctx.loader)
            resolveTypes(method, call.args)
        }
        is StaticMethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.klass)
            val method = reflection.getMethod(call.method, ctx.loader)
            resolveTypes(method, call.args)
        }
        else -> {
        }
    }

    protected open fun CallStack.printAsJava() {
        if (name in printedStacks) return
        if (this is PrimaryValue<*>) {
            asConstant
            return
        }
        printedStacks += name
        for (call in this) {
            with(current) {
                for (statement in printApiCall(this@printAsJava, call))
                    +statement
            }
        }
    }

    protected val Class.javaString: String get() = this.type.javaString

    protected val Type.javaString: String
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
                val klass = (this as ClassType).klass
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

    protected fun printApiCall(owner: CallStack, apiCall: ApiCall): List<String> = when (apiCall) {
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
    }

    protected val <T> PrimaryValue<T>.asConstant: String
        get() = when (val value = value) {
            null -> "null"
            is Boolean -> "$value".also {
                actualTypes[this] = CSClass(ctx.types.boolType)
            }
            is Byte -> "(byte) $value".also {
                actualTypes[this] = CSClass(ctx.types.byteType)
            }
            is Char -> when (value) {
                in 'a'..'z' -> "'$value'"
                in 'A'..'Z' -> "'$value'"
                else -> "(char) ${value.code}"
            }.also {
                actualTypes[this] = CSClass(ctx.types.charType)
            }
            is Short -> "(short) $value".also {
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
            else -> unreachable { log.error("Unknown primary value $this") }
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

    protected open fun printVarDeclaration(name: String, type: CSType): String = "$type $name"

    protected open fun printDefaultConstructor(owner: CallStack, call: DefaultConstructorCall): List<String> {
        val actualType = CSClass(call.klass.type)
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${printVarDeclaration(owner.name, type)} = new $type()"
            } else {
                actualTypes[owner] = actualType
                "${printVarDeclaration(owner.name, actualType)} = new $actualType()"
            }
        )
    }

    protected open fun printConstructorCall(owner: CallStack, call: ConstructorCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = CSClass(call.constructor.klass.type)
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${printVarDeclaration(owner.name, type)} = new $type($args)"
            } else {
                actualTypes[owner] = actualType
                "${printVarDeclaration(owner.name, actualType)} = new $actualType($args)"
            }
        )
    }

    protected open fun innerClassName(innerType: CSType, outerType: CSType, reqOuterType: CSType?): String {
        if (innerType !is CSClass) return innerType.toString()
        if (outerType !is CSClass) return innerType.toString()

        val innerString = (innerType.type as? ClassType)?.klass?.fullName ?: return innerType.toString()
        val outerString = (outerType.type as? ClassType)?.klass?.fullName ?: return innerType.toString()
        if (reqOuterType != null && reqOuterType is CSClass) {
            val reqTypeString = (reqOuterType.type as ClassType).klass.fullName
            if (innerString.startsWith(reqTypeString))
                return innerString.removePrefix("$reqTypeString\$").replace('/', '.')
        }
        if (innerString.startsWith(outerString))
            return innerString.removePrefix("$outerString\$").replace('/', '.')
        TODO()
    }

    protected open fun printInnerClassConstructor(owner: CallStack, call: InnerClassConstructorCall): List<String> {
        call.outerObject.printAsJava()
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = CSClass(call.constructor.klass.type)
        val outerObject = call.outerObject.forceCastIfNull(resolvedTypes[call.outerObject])
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                val innerClassName =
                    innerClassName(type, actualTypes[call.outerObject]!!, resolvedTypes[call.outerObject])
                "${printVarDeclaration(owner.name, type)} = $outerObject.new $innerClassName($args)"
            } else {
                actualTypes[owner] = actualType
                val innerClassName =
                    innerClassName(actualType, actualTypes[call.outerObject]!!, resolvedTypes[call.outerObject])
                "${printVarDeclaration(owner.name, actualType)} = $outerObject.new $innerClassName($args)"
            }
        )
    }

    protected open fun printExternalConstructorCall(owner: CallStack, call: ExternalConstructorCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val constructor = call.constructor
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = CSClass(constructor.returnType)
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${printVarDeclaration(owner.name, type)} = ${constructor.klass.javaString}.${constructor.name}($args)"
            } else {
                actualTypes[owner] = actualType
                "${
                    printVarDeclaration(
                        owner.name,
                        actualType
                    )
                } = ${constructor.klass.javaString}.${constructor.name}($args)"
            }
        )
    }

    protected open fun printMethodCall(owner: CallStack, call: MethodCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return listOf("${owner.name}.${method.name}($args)")
    }

    protected open fun printStaticMethodCall(call: StaticMethodCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val klass = call.method.klass
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return listOf("${klass.javaString}.${method.name}($args)")
    }

    protected open fun CSType.elementTypeDepth(depth: Int = -1): Pair<Int, CSType> = when (this) {
        is CSArray -> this.element.elementTypeDepth(depth + 1)
        is CSPrimaryArray -> this.element.elementTypeDepth(depth + 1)
        else -> depth to this
    }

    protected open fun printNewArray(owner: CallStack, call: NewArray): List<String> {
        val actualType = call.asArray.csType
        val (depth, elementType) = actualType.elementTypeDepth()
        actualTypes[owner] = actualType
        return listOf(
            "${printVarDeclaration(owner.name, actualType)} = new $elementType[${call.length.stackName}]${"[]".repeat(depth)}"
        )
    }

    private fun lub(lhv: CSType?, rhv: CSType?): CSType = when {
        lhv == null -> rhv!!
        rhv == null -> lhv
        lhv.isSubtype(rhv) -> lhv
        rhv.isSubtype(lhv) -> rhv
        else -> unreachable {  }
    }

    private val CSType.elementType: CSType get() = when (this) {
        is CSPrimaryArray -> this.element
        is CSArray -> this.element
        else -> TODO()
    }

    protected open fun printArrayWrite(owner: CallStack, call: ArrayWrite): List<String> {
        call.value.printAsJava()
        val requiredType = lub(resolvedTypes[owner]?.elementType, actualTypes[owner]?.elementType)
        return listOf("${owner.name}[${call.index.stackName}] = ${call.value.cast(requiredType)}")
    }

    protected open fun printFieldSetter(owner: CallStack, call: FieldSetter): List<String> {
        call.value.printAsJava()
        return listOf("${owner.name}.${call.field.name} = ${call.value.stackName}")
    }

    protected open fun printStaticFieldSetter(call: StaticFieldSetter): List<String> {
        call.value.printAsJava()
        return listOf("${call.field.klass.javaString}.${call.field.name} = ${call.value.stackName}")
    }

    protected open fun printEnumValueCreation(owner: CallStack, call: EnumValueCreation): List<String> {
        val actualType = call.klass.type.csType
        actualTypes[owner] = actualType
        return listOf("${printVarDeclaration(owner.name, actualType)} = ${call.klass.javaString}.${call.name}")
    }

    protected open fun printStaticFieldGetter(owner: CallStack, call: StaticFieldGetter): List<String> {
        val actualType = call.field.klass.type.csType
        actualTypes[owner] = actualType
        return listOf("${printVarDeclaration(owner.name, actualType)} = ${call.field.klass.javaString}.${call.field.name}")
    }

    protected open fun printUnknown(owner: CallStack, call: UnknownCall): List<String> {
        val type = call.target.type.csType
        actualTypes[owner] = type
        return listOf("${printVarDeclaration(owner.name, type)} = unknown()")
    }
}
