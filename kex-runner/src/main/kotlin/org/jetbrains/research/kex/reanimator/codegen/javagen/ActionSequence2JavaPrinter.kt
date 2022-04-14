package org.jetbrains.research.kex.reanimator.codegen.javagen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.actionsequence.*
import org.jetbrains.research.kex.reanimator.codegen.ActionSequencePrinter
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
import org.jetbrains.research.kthelper.tryOrNull
import java.lang.reflect.*

private val visibilityLevel by lazy {
    kexConfig.getEnumValue("testGen", "visibility", true, Visibility.PUBLIC)
}

// TODO: this is work of satan, refactor this damn thing
open class ActionSequence2JavaPrinter(
    val ctx: ExecutionContext,
    final override val packageName: String,
    final override val klassName: String
) : ActionSequencePrinter {
    protected val printedStacks = mutableSetOf<String>()
    protected val builder = JavaBuilder(packageName)
    protected val klass = builder.run { klass(packageName, klassName) }
    protected val resolvedTypes = mutableMapOf<ActionSequence, ASType>()
    protected val actualTypes = mutableMapOf<ActionSequence, ASType>()
    lateinit var current: JavaBuilder.JavaFunction
    protected var testCounter = 0

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

    private fun buildMethodCall(
        method: org.jetbrains.research.kfg.ir.Method, actionSequences: Parameters<ActionSequence>
    ): ActionSequence = when {
        method.isStatic -> TestCall("test${testCounter++}", method, null, actionSequences.arguments)
        method.isConstructor -> actionSequences.instance!!
        else -> TestCall("test${testCounter++}", method, actionSequences.instance, actionSequences.arguments)
    }

    protected open fun cleanup() {
        printedStacks.clear()
        resolvedTypes.clear()
        actualTypes.clear()
    }

    override fun printActionSequence(
        testName: String,
        method: org.jetbrains.research.kfg.ir.Method,
        actionSequences: Parameters<ActionSequence>
    ) {
        cleanup()
        val actionSequence = buildMethodCall(method, actionSequences)
        with(builder) {
            with(klass) {
                current = method(testName) {
                    returnType = void
                    annotations += "Test"
                    exceptions += "Throwable"
                }
            }
        }
        resolveTypes(actionSequence)
        tryOrNull { actionSequence.printAsJava() }
    }

    override fun emit() = builder.toString()


    interface ASType {
        fun isSubtype(other: ASType): Boolean
    }

    inner class ASWildcard(val upperBound: ASType, val lowerBound: ASType? = null) : ASType {
        override fun isSubtype(other: ASType) = other is ASWildcard
        override fun toString() = when {
            lowerBound != null -> "? super $lowerBound"
            else -> "? extends $upperBound"
        }
    }

    inner class ASClass(val type: Type, val typeParams: List<ASType> = emptyList()) : ASType {
        override fun isSubtype(other: ASType): Boolean = when (other) {
            is ASClass -> when {
                !type.isSubtypeOf(other.type) -> false
                typeParams.isEmpty() && other.typeParams.isNotEmpty() -> true
                typeParams.size != other.typeParams.size -> false
                else -> typeParams.zip(other.typeParams).all { (a, b) -> a.isSubtype(b) }
            }
            is ASWildcard -> true
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

    inner class ASPrimaryArray(val element: ASType) : ASType {
        override fun isSubtype(other: ASType): Boolean = when (other) {
            is ASPrimaryArray -> element.isSubtype(other.element)
            is ASWildcard -> true
            else -> other.kfg == ctx.types.objectType
        }

        override fun toString() = "${element}[]"
    }

    inner class ASArray(val element: ASType) : ASType {
        override fun isSubtype(other: ASType): Boolean = when (other) {
            is ASArray -> when {
                !element.isSubtype(other.element) -> false
                else -> true
            }
            is ASWildcard -> true
            else -> false
        }

        override fun toString() = "${element}[]"
    }

    val ASType.kfg: Type
        get() = when (this) {
            is ASClass -> type
            is ASArray -> ctx.types.getArrayType(element.kfg)
            is ASPrimaryArray -> ctx.types.getArrayType(element.kfg)
            else -> unreachable { }
        }

    protected val java.lang.reflect.Type.asType: ASType
        get() = when (this) {
            is java.lang.Class<*> -> when {
                this.isArray -> {
                    val element = this.componentType.asType
                    if (this.componentType.isPrimitive) ASPrimaryArray(element)
                    else ASArray(element)
                }
                else -> ASClass(this.kex.getKfgType(ctx.types))
            }
            is ParameterizedType -> {
                val rawType = this.rawType.asType.kfg
                val typeArgs = this.actualTypeArguments.map { it.asType }
                ASClass(rawType, typeArgs)
            }
            is GenericArrayType -> ASArray(this.genericComponentType.asType)
            is TypeVariable<*> -> this.bounds.first().asType
            is WildcardType -> ASWildcard(
                upperBounds?.firstOrNull()?.asType ?: ctx.types.objectType.asType,
                lowerBounds?.firstOrNull()?.asType
            )
            else -> TODO()
        }

    protected fun ASType.merge(requiredType: ASType): ASType = when {
        this is ASClass && requiredType is ASClass -> {
            val actualKlass = ctx.loader.loadClass(type)
            val requiredKlass = ctx.loader.loadClass(requiredType.type)
            val isAssignable = requiredKlass.isAssignableFrom(actualKlass)
            val isVisible = ((type as? ClassType)?.klass?.visibility ?: Visibility.PUBLIC) >= visibilityLevel
            if (isVisible && isAssignable && actualKlass.typeParameters.size == requiredKlass.typeParameters.size) {
                ASClass(
                    type,
                    this.typeParams.zip(requiredType.typeParams).map { (f, s) -> if (f.isSubtype(s)) f else s }
                )
            } else if (isVisible && isAssignable) {
                ASClass(type)
            } else {
                requiredType
            }
        }
        else -> TODO()
    }

    private fun ASType?.isAssignable(other: ASType) = this?.let { other.isSubtype(it) } ?: true

    private val KexType.asType get() = this.getKfgType(ctx.types).asType

    protected val Type.asType: ASType
        get() = when (this) {
            is ArrayType -> when {
                this.component.isPrimary -> ASPrimaryArray(component.asType)
                else -> ASArray(this.component.asType)
            }
            else -> ASClass(this)
        }

    protected fun resolveTypes(actionSequence: ActionSequence) {
        when (actionSequence) {
            is ActionList -> actionSequence.reversed().map { resolveTypes(it) }
            is TestCall -> {
                actionSequence.instance?.let { resolveTypes(it) }
                actionSequence.args.forEach { resolveTypes(it) }
            }
            else -> {}
        }
    }

    private fun resolveTypes(constructor: Constructor<*>, args: List<ActionSequence>) {
        val params = constructor.genericParameterTypes
        args.zip(params).forEach { (arg, param) ->
            if (arg !in resolvedTypes) {
                resolvedTypes[arg] = param.asType
                resolveTypes(arg)
            }
        }
    }

    private fun resolveTypes(method: Method, args: List<ActionSequence>) {
        val params = method.genericParameterTypes.toList()
        args.zip(params).forEach { (arg, param) ->
            if (arg !in resolvedTypes) {
                resolvedTypes[arg] = param.asType
                resolveTypes(arg)
            }
        }
    }

    private fun resolveTypes(call: CodeAction) = when (call) {
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
        is ExternalMethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.klass)
            val constructor = reflection.getMethod(call.method, ctx.loader)
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

    protected open fun ActionSequence.printAsJava() {
        if (name in printedStacks) return
        printedStacks += name
        val statements = when (this) {
            is TestCall -> printTestCall(this)
            is UnknownSequence -> printUnknownSequence(this)
            is ActionList -> this.flatMap { printApiCall(this, it) }
            is PrimaryValue<*> -> listOf<String>().also {
                asConstant
            }
        }
        with(current) {
            for (statement in statements)
                +statement
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

    protected val ActionSequence.stackName: String
        get() = when (this) {
            is PrimaryValue<*> -> asConstant
            else -> name
        }

    protected fun printApiCall(owner: ActionSequence, codeAction: CodeAction): List<String> = when (codeAction) {
        is DefaultConstructorCall -> printDefaultConstructor(owner, codeAction)
        is ConstructorCall -> printConstructorCall(owner, codeAction)
        is ExternalConstructorCall -> printExternalConstructorCall(owner, codeAction)
        is ExternalMethodCall -> printExternalMethodCall(owner, codeAction)
        is InnerClassConstructorCall -> printInnerClassConstructor(owner, codeAction)
        is MethodCall -> printMethodCall(owner, codeAction)
        is StaticMethodCall -> printStaticMethodCall(codeAction)
        is NewArray -> printNewArray(owner, codeAction)
        is ArrayWrite -> printArrayWrite(owner, codeAction)
        is FieldSetter -> printFieldSetter(owner, codeAction)
        is StaticFieldSetter -> printStaticFieldSetter(codeAction)
        is EnumValueCreation -> printEnumValueCreation(owner, codeAction)
        is StaticFieldGetter -> printStaticFieldGetter(owner, codeAction)
    }

    protected val <T> PrimaryValue<T>.asConstant: String
        get() = when (val value = value) {
            null -> "null"
            is Boolean -> "$value".also {
                actualTypes[this] = ASClass(ctx.types.boolType)
            }
            is Byte -> "(byte) $value".also {
                actualTypes[this] = ASClass(ctx.types.byteType)
            }
            is Char -> when (value) {
                in 'a'..'z' -> "'$value'"
                in 'A'..'Z' -> "'$value'"
                else -> "(char) ${value.code}"
            }.also {
                actualTypes[this] = ASClass(ctx.types.charType)
            }
            is Short -> "(short) $value".also {
                actualTypes[this] = ASClass(ctx.types.shortType)
            }
            is Int -> "$value".also {
                actualTypes[this] = ASClass(ctx.types.intType)
            }
            is Long -> "${value}L".also {
                actualTypes[this] = ASClass(ctx.types.longType)
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
                actualTypes[this] = ASClass(ctx.types.floatType)
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
                actualTypes[this] = ASClass(ctx.types.doubleType)
            }
            else -> unreachable { log.error("Unknown primary value $this") }
        }

    protected fun ActionSequence.cast(reqType: ASType?): String {
        val actualType = actualTypes[this] ?: return this.stackName
        return when {
            reqType.isAssignable(actualType) -> this.stackName
            else -> "($reqType)${this.stackName}"
        }
    }

    protected fun ActionSequence.forceCastIfNull(reqType: ASType?): String = when {
        this.stackName == "null" && reqType != null -> "($reqType)${this.stackName}"
        else -> this.cast(reqType)
    }

    protected fun ASType.cast(reqType: ASType?): String {
        return when {
            reqType.isAssignable(this) -> ""
            else -> "($reqType)"
        }
    }

    protected open fun printVarDeclaration(name: String, type: ASType): String = "$type $name"

    protected open fun printDefaultConstructor(owner: ActionSequence, call: DefaultConstructorCall): List<String> {
        val actualType = ASClass(call.klass.type)
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

    protected open fun printConstructorCall(owner: ActionSequence, call: ConstructorCall): List<String> {
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = ASClass(call.constructor.klass.type)
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

    protected open fun innerClassName(innerType: ASType, outerType: ASType, reqOuterType: ASType?): String {
        if (innerType !is ASClass) return innerType.toString()
        if (outerType !is ASClass) return innerType.toString()

        val innerString = (innerType.type as? ClassType)?.klass?.fullName ?: return innerType.toString()
        val outerString = (outerType.type as? ClassType)?.klass?.fullName ?: return innerType.toString()
        if (reqOuterType != null && reqOuterType is ASClass) {
            val reqTypeString = (reqOuterType.type as ClassType).klass.fullName
            if (innerString.startsWith(reqTypeString))
                return innerString.removePrefix("$reqTypeString\$").replace('/', '.')
        }
        if (innerString.startsWith(outerString))
            return innerString.removePrefix("$outerString\$").replace('/', '.')
        TODO()
    }

    protected open fun printInnerClassConstructor(
        owner: ActionSequence,
        call: InnerClassConstructorCall
    ): List<String> {
        call.outerObject.printAsJava()
        call.args.forEach { it.printAsJava() }
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = ASClass(call.constructor.klass.type)
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

    protected open fun printExternalConstructorCall(
        owner: ActionSequence,
        call: ExternalConstructorCall
    ): List<String> {
        call.args.forEach { it.printAsJava() }
        val constructor = call.constructor
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }

        val reflection = ctx.loader.loadClass(call.constructor.klass)
        val ctor = reflection.getMethod(call.constructor, ctx.loader)
        val actualType = ctor.genericReturnType.asType
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${
                    printVarDeclaration(
                        owner.name,
                        type
                    )
                } = ${type.cast(rest)} ${constructor.klass.javaString}.${constructor.name}($args)"
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

    protected open fun printExternalMethodCall(
        owner: ActionSequence,
        call: ExternalMethodCall
    ): List<String> {
        call.instance.printAsJava()
        call.args.forEach { it.printAsJava() }
        val method = call.method
        val instance = "(${call.instance.forceCastIfNull(resolvedTypes[call.instance])})"
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }

        val reflection = ctx.loader.loadClass(call.method.klass)
        val ctor = reflection.getMethod(call.method, ctx.loader)
        val actualType = ctor.genericReturnType.asType
        return listOf(
            if (resolvedTypes[owner] != null) {
                val rest = resolvedTypes[owner]!!
                val type = actualType.merge(rest)
                actualTypes[owner] = type
                "${
                    printVarDeclaration(
                        owner.name,
                        type
                    )
                } = ${type.cast(rest)} $instance.${method.name}($args)"
            } else {
                actualTypes[owner] = actualType
                "${
                    printVarDeclaration(
                        owner.name,
                        actualType
                    )
                } = $instance.${method.name}($args)"
            }
        )
    }

    protected open fun printMethodCall(owner: ActionSequence, call: MethodCall): List<String> {
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

    protected open fun ASType.elementTypeDepth(depth: Int = -1): Pair<Int, ASType> = when (this) {
        is ASArray -> this.element.elementTypeDepth(depth + 1)
        is ASPrimaryArray -> this.element.elementTypeDepth(depth + 1)
        else -> depth to this
    }

    protected open fun printNewArray(owner: ActionSequence, call: NewArray): List<String> {
        val actualType = call.asArray.asType
        val (depth, elementType) = actualType.elementTypeDepth()
        actualTypes[owner] = actualType
        return listOf(
            "${printVarDeclaration(owner.name, actualType)} = new $elementType[${call.length.stackName}]${
                "[]".repeat(
                    depth
                )
            }"
        )
    }

    private fun lub(lhv: ASType?, rhv: ASType?): ASType = when {
        lhv == null -> rhv!!
        rhv == null -> lhv
        lhv.isSubtype(rhv) -> lhv
        rhv.isSubtype(lhv) -> rhv
        else -> unreachable { }
    }

    private val ASType.elementType: ASType
        get() = when (this) {
            is ASPrimaryArray -> this.element
            is ASArray -> this.element
            else -> TODO()
        }

    protected open fun printArrayWrite(owner: ActionSequence, call: ArrayWrite): List<String> {
        call.value.printAsJava()
        val requiredType = lub(resolvedTypes[owner]?.elementType, actualTypes[owner]?.elementType)
        return listOf("${owner.name}[${call.index.stackName}] = ${call.value.cast(requiredType)}")
    }

    protected open fun printFieldSetter(owner: ActionSequence, call: FieldSetter): List<String> {
        call.value.printAsJava()
        return listOf("${owner.name}.${call.field.name} = ${call.value.stackName}")
    }

    protected open fun printStaticFieldSetter(call: StaticFieldSetter): List<String> {
        call.value.printAsJava()
        return listOf("${call.field.klass.javaString}.${call.field.name} = ${call.value.stackName}")
    }

    protected open fun printEnumValueCreation(owner: ActionSequence, call: EnumValueCreation): List<String> {
        val actualType = call.klass.type.asType
        actualTypes[owner] = actualType
        return listOf("${printVarDeclaration(owner.name, actualType)} = ${call.klass.javaString}.${call.name}")
    }

    protected open fun printStaticFieldGetter(owner: ActionSequence, call: StaticFieldGetter): List<String> {
        val actualType = call.field.type.asType
        actualTypes[owner] = actualType
        return listOf(
            "${
                printVarDeclaration(
                    owner.name,
                    actualType
                )
            } = ${call.field.klass.javaString}.${call.field.name}"
        )
    }

    protected open fun printTestCall(sequence: TestCall): List<String> {
        sequence.instance?.printAsJava()
        sequence.args.forEach { it.printAsJava() }
        val callee = when (sequence.instance) {
            null -> sequence.test.klass.javaString
            else -> sequence.instance.name
        }
        val args = sequence.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return listOf("$callee.${sequence.test.name}($args)")
    }

    protected open fun printUnknownSequence(sequence: UnknownSequence): List<String> {
        val actualType = sequence.target.type.asType
        return listOf(
            if (resolvedTypes[sequence] != null) {
                val rest = resolvedTypes[sequence]!!
                val type = actualType.merge(rest)
                actualTypes[sequence] = type
                "${printVarDeclaration(sequence.name, actualType)} = unknown()"
            } else {
                actualTypes[sequence] = actualType
                "${printVarDeclaration(sequence.name, actualType)} = unknown()"
            }
        )
    }
}
