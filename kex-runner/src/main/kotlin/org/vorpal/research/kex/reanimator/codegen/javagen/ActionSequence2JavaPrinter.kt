package org.vorpal.research.kex.reanimator.codegen.javagen

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.asm.util.accessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ArrayClassConstantGetter
import org.vorpal.research.kex.reanimator.actionsequence.ArrayWrite
import org.vorpal.research.kex.reanimator.actionsequence.ClassConstantGetter
import org.vorpal.research.kex.reanimator.actionsequence.CodeAction
import org.vorpal.research.kex.reanimator.actionsequence.ConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.EnumValueCreation
import org.vorpal.research.kex.reanimator.actionsequence.ExternalConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.ExternalMethodCall
import org.vorpal.research.kex.reanimator.actionsequence.FieldSetter
import org.vorpal.research.kex.reanimator.actionsequence.InnerClassConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kex.reanimator.actionsequence.NewArray
import org.vorpal.research.kex.reanimator.actionsequence.NewArrayWithInitializer
import org.vorpal.research.kex.reanimator.actionsequence.PrimaryValue
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionArrayWrite
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionCall
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionList
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionNewArray
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionNewInstance
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionSetField
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionSetStaticField
import org.vorpal.research.kex.reanimator.actionsequence.StaticFieldGetter
import org.vorpal.research.kex.reanimator.actionsequence.StaticFieldSetter
import org.vorpal.research.kex.reanimator.actionsequence.StaticMethodCall
import org.vorpal.research.kex.reanimator.actionsequence.StringValue
import org.vorpal.research.kex.reanimator.actionsequence.TestCall
import org.vorpal.research.kex.reanimator.actionsequence.UnknownSequence
import org.vorpal.research.kex.reanimator.codegen.ActionSequencePrinter
import org.vorpal.research.kex.util.getConstructor
import org.vorpal.research.kex.util.getMethod
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kex.util.kex
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.BoolType
import org.vorpal.research.kfg.type.ByteType
import org.vorpal.research.kfg.type.CharType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.DoubleType
import org.vorpal.research.kfg.type.FloatType
import org.vorpal.research.kfg.type.IntType
import org.vorpal.research.kfg.type.LongType
import org.vorpal.research.kfg.type.NullType
import org.vorpal.research.kfg.type.ShortType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.VoidType
import org.vorpal.research.kfg.type.classType
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.lang.reflect.Constructor
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

private val testTimeout by lazy {
    kexConfig.getIntValue("testGen", "testTimeout", 10)
}

// TODO: this is work of satan, refactor this damn thing
@Suppress("MemberVisibilityCanBePrivate")
open class ActionSequence2JavaPrinter(
    val ctx: ExecutionContext,
    final override val packageName: String,
    final override val klassName: String
) : ActionSequencePrinter {
    protected val generateSetup = kexConfig.getBooleanValue("testGen", "generateSetup", false)
    protected val printedStacks = mutableSetOf<String>()
    protected val builder = JavaBuilder(packageName)
    protected val klass = builder.run { klass(packageName, klassName) }
    protected val resolvedTypes = mutableMapOf<ActionSequence, ASType>()
    protected val actualTypes = mutableMapOf<ActionSequence, ASType>()
    lateinit var current: JavaBuilder.JavaFunction
    private var testCounter = 0


    init {
        with(builder) {
            import("java.lang.Throwable")
            import("java.lang.IllegalStateException")
            import("org.junit.Test")
            import("org.junit.Rule")
            import("org.junit.rules.Timeout")
            import("java.util.concurrent.TimeUnit")

            with(klass) {
                constructor {}

                field("globalTimeout", type("Timeout")) {
                    visibility = Visibility.PUBLIC
                    initializer = "new Timeout($testTimeout, TimeUnit.SECONDS)"
                    annotations += "Rule"
                }

                method("unknown", listOf(type("T"))) {
                    returnType = type("T")
                    +"throw new IllegalStateException()"
                }
            }
        }
    }

    private fun buildMethodCall(
        method: org.vorpal.research.kfg.ir.Method, actionSequences: Parameters<ActionSequence>
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
        method: org.vorpal.research.kfg.ir.Method,
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

    inner class ASWildcard(
        private val upperBound: ASType,
        private val lowerBound: ASType? = null
    ) : ASType {
        override fun isSubtype(other: ASType) = other is ASWildcard
        override fun toString() = when {
            lowerBound != null -> "? super $lowerBound"
            else -> "? extends $upperBound"
        }
    }

    inner class ASClass(val type: Type, val typeParams: List<ASType> = emptyList()) : ASType {
        override fun isSubtype(other: ASType): Boolean = when (other) {
            is ASClass -> when {
                !type.isSubtypeOfCached(other.type) -> false
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

    @Suppress("RecursivePropertyAccessor")
    val ASType.kfg: Type
        get() = when (this) {
            is ASClass -> type
            is ASArray -> ctx.types.getArrayType(element.kfg)
            is ASPrimaryArray -> ctx.types.getArrayType(element.kfg)
            else -> unreachable { }
        }

    @Suppress("RecursivePropertyAccessor")
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
            val isVisible = ctx.accessLevel.canAccess(type.accessModifier)
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

    @Suppress("RecursivePropertyAccessor")
    protected val Type.asType: ASType
        get() = when (this) {
            is ArrayType -> when {
                this.component.isPrimitive -> ASPrimaryArray(component.asType)
                else -> ASArray(this.component.asType)
            }

            else -> ASClass(this)
        }

    protected fun resolveTypes(
        actionSequence: ActionSequence,
        visited: MutableSet<String> = mutableSetOf()
    ) {
        if (actionSequence.name in visited) return
        visited += actionSequence.name

        when (actionSequence) {
            is ActionList -> actionSequence.reversed().map { resolveTypes(it, visited) }
            is ReflectionList -> actionSequence.reversed().map { resolveTypes(it, visited) }
            is TestCall -> {
                actionSequence.instance?.let { resolveTypes(it, visited) }
                actionSequence.args.forEach { resolveTypes(it, visited) }
            }

            else -> {}
        }
    }

    private fun resolveTypes(constructor: Constructor<*>, args: List<ActionSequence>, visited: MutableSet<String>) {
        val params = constructor.genericParameterTypes
        args.zip(params).forEach { (arg, param) ->
            if (arg !in resolvedTypes) {
                resolvedTypes[arg] = param.asType
                resolveTypes(arg, visited)
            }
        }
    }

    private fun resolveTypes(method: Method, args: List<ActionSequence>, visited: MutableSet<String>) {
        val params = method.genericParameterTypes.toList()
        args.zip(params).forEach { (arg, param) ->
            if (arg !in resolvedTypes) {
                resolvedTypes[arg] = param.asType
                resolveTypes(arg, visited)
            }
        }
    }

    private fun resolveTypes(call: CodeAction, visited: MutableSet<String>) = when (call) {
        is DefaultConstructorCall -> {
        }

        is ConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.constructor.klass)
            val constructor = reflection.getConstructor(call.constructor, ctx.loader)
            resolveTypes(constructor, call.args, visited)
        }

        is ExternalConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.constructor.klass)
            val constructor = reflection.getMethod(call.constructor, ctx.loader)
            resolveTypes(constructor, call.args, visited)
        }

        is ExternalMethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.klass)
            val constructor = reflection.getMethod(call.method, ctx.loader)
            resolveTypes(call.instance)
            resolveTypes(constructor, call.args, visited)
        }

        is InnerClassConstructorCall -> {
            val reflection = ctx.loader.loadClass(call.constructor.klass)
            val constructor = reflection.getConstructor(call.constructor, ctx.loader)
            resolveTypes(call.outerObject, visited)
            resolveTypes(constructor, call.args, visited)
        }

        is MethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.klass)
            val method = reflection.getMethod(call.method, ctx.loader)
            resolveTypes(method, call.args, visited)
        }

        is StaticMethodCall -> {
            val reflection = ctx.loader.loadClass(call.method.klass)
            val method = reflection.getMethod(call.method, ctx.loader)
            resolveTypes(method, call.args, visited)
        }

        else -> {
        }
    }

    private fun resolveTypes(reflectionCall: ReflectionCall, visited: MutableSet<String>) = when (reflectionCall) {
        is ReflectionArrayWrite -> resolveTypes(reflectionCall.value, visited)
        is ReflectionSetField -> resolveTypes(reflectionCall.value, visited)
        is ReflectionSetStaticField -> resolveTypes(reflectionCall.value, visited)
        is ReflectionNewArray -> {}
        is ReflectionNewInstance -> {}
    }

    protected open fun ActionSequence.printAsJava() {
        if (name in printedStacks) return
        printedStacks += name
        val statements = when (this) {
            is TestCall -> printTestCall(this)
            is UnknownSequence -> printUnknownSequence(this)
            is ActionList -> printActionList(this)
            is ReflectionList -> printReflectionList(this)
            is PrimaryValue<*> -> listOf<String>().also {
                asConstant
            }

            is StringValue -> listOf<String>().also {
                asConstant
            }
        }
        with(current) {
            for (statement in statements)
                +statement
        }
    }

    protected open fun printActionList(actionList: ActionList): List<String> =
        actionList.flatMap { printApiCall(actionList, it) }

    protected open fun printReflectionList(reflectionList: ReflectionList): List<String> =
        reflectionList.flatMap { printReflectionCall(reflectionList, it) }

    protected val Class.javaString: String get() = this.asType.javaString

    @Suppress("RecursivePropertyAccessor")
    protected val Type.javaString: String
        get() = when (this) {
            is NullType -> "null"
            is VoidType -> "void"
            is BoolType -> "boolean"
            is ByteType -> "byte"
            is ShortType -> "short"
            is CharType -> "char"
            is IntType -> "int"
            is LongType -> "long"
            is FloatType -> "float"
            is DoubleType -> "double"
            is ArrayType -> "${this.component.javaString}[]"
            else -> {
                val klass = (this as ClassType).klass
                val canonicalDesc = klass.canonicalDesc
                val splitDescriptor = canonicalDesc.split("$")
                ktassert(splitDescriptor.isNotEmpty())
                buildString {
                    append(splitDescriptor[0])
                    builder.import(this.toString())
                    for (substring in splitDescriptor.drop(1)) {
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
            is StringValue -> asConstant
            else -> name
        }

    protected fun printReflectionCall(owner: ActionSequence, reflectionCall: ReflectionCall): List<String> =
        when (reflectionCall) {
            is ReflectionArrayWrite -> printReflectionArrayWrite(owner, reflectionCall)
            is ReflectionNewArray -> printReflectionNewArray(owner, reflectionCall)
            is ReflectionNewInstance -> printReflectionNewInstance(owner, reflectionCall)
            is ReflectionSetField -> printReflectionSetField(owner, reflectionCall)
            is ReflectionSetStaticField -> printReflectionSetStaticField(owner, reflectionCall)
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
        is NewArrayWithInitializer -> printNewArrayWithInitializer(owner, codeAction)
        is FieldSetter -> printFieldSetter(owner, codeAction)
        is StaticFieldSetter -> printStaticFieldSetter(codeAction)
        is EnumValueCreation -> printEnumValueCreation(owner, codeAction)
        is StaticFieldGetter -> printStaticFieldGetter(owner, codeAction)
        is ClassConstantGetter -> printClassConstantGetter(owner, codeAction)
        is ArrayClassConstantGetter -> printArrayClassConstantGetter(owner, codeAction)
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

    protected val StringValue.asConstant: String
        get() {
            val escapedValue = value.map { JavaBuilder.escapeCharIfNeeded(it) }.joinToString("")
            return "\"$escapedValue\"".also {
                actualTypes[this] = ASClass(ctx.types.nullType)
            }
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
        val actualType = ASClass(call.klass.asType)
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
        val actualType = ASClass(call.constructor.klass.asType)
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
                return innerString.removePrefix("$reqTypeString\$").javaString
        }
        if (innerString.startsWith(outerString))
            return innerString.removePrefix("$outerString\$").javaString
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
        val actualType = ASClass(call.constructor.klass.asType)
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

    protected fun printConstructorCommon(
        owner: ActionSequence,
        call: ExternalConstructorCall,
        argsPrinter: (List<ActionSequence>) -> String
    ): List<String> {
        call.args.forEach { it.printAsJava() }
        val constructor = call.constructor
        val args = argsPrinter(call.args)

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

    protected open fun printExternalConstructorCall(
        owner: ActionSequence,
        call: ExternalConstructorCall
    ): List<String> = printConstructorCommon(owner, call) { args ->
        args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
    }

    protected fun printExternalMethodCallCommon(
        owner: ActionSequence,
        call: ExternalMethodCall,
        instancePrinter: (ActionSequence) -> String,
        argsPrinter: (List<ActionSequence>) -> String
    ): List<String> {
        call.instance.printAsJava()
        call.args.forEach { it.printAsJava() }
        val method = call.method
        val instance = instancePrinter(call.instance)
        val args = argsPrinter(call.args)

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

    protected open fun printExternalMethodCall(
        owner: ActionSequence,
        call: ExternalMethodCall
    ): List<String> = printExternalMethodCallCommon(
        owner,
        call,
        { instance -> "(${instance.forceCastIfNull(resolvedTypes[instance])})" },
        { args ->
            args.joinToString(", ") {
                it.forceCastIfNull(resolvedTypes[it])
            }
        }
    )

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

    protected open fun printNewArrayWithInitializer(
        owner: ActionSequence,
        call: NewArrayWithInitializer
    ): List<String> {
        call.elements.forEach { it.printAsJava() }
        val actualType = call.asArray.asType
        actualTypes[owner] = actualType
        return listOf(
            "${printVarDeclaration(owner.name, actualType)} = ${
                call.elements.joinToString(
                    separator = ", ",
                    prefix = "{",
                    postfix = "}"
                ) { it.stackName }
            }"
        )
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
        val actualType = call.klass.asType.asType
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

    protected open fun printClassConstantGetter(owner: ActionSequence, call: ClassConstantGetter): List<String> {
        val actualType = ASClass(ctx.types.classType)
        actualTypes[owner] = actualType
        return listOf(
            "${
                printVarDeclaration(
                    owner.name,
                    actualType
                )
            } = ${call.type.javaString}.class"
        )
    }

    protected open fun printArrayClassConstantGetter(owner: ActionSequence, call: ArrayClassConstantGetter): List<String> {
        call.elementType.printAsJava()
        val actualType = ASClass(ctx.types.classType)
        actualTypes[owner] = actualType
        return listOf(
            "${
                printVarDeclaration(
                    owner.name,
                    actualType
                )
            } = Array.newInstance(${call.elementType.stackName}, 0).getClass()"
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

    protected open fun printReflectionNewInstance(owner: ActionSequence, call: ReflectionNewInstance): List<String> =
        unreachable { log.error("Reflection calls are not supported in AS 2 Java printer") }

    protected open fun printReflectionNewArray(owner: ActionSequence, call: ReflectionNewArray): List<String> =
        unreachable { log.error("Reflection calls are not supported in AS 2 Java printer") }

    protected open fun printReflectionSetField(owner: ActionSequence, call: ReflectionSetField): List<String> =
        unreachable { log.error("Reflection calls are not supported in AS 2 Java printer") }

    protected open fun printReflectionSetStaticField(
        owner: ActionSequence,
        call: ReflectionSetStaticField
    ): List<String> =
        unreachable { log.error("Reflection calls are not supported in AS 2 Java printer") }

    protected open fun printReflectionArrayWrite(owner: ActionSequence, call: ReflectionArrayWrite): List<String> =
        unreachable { log.error("Reflection calls are not supported in AS 2 Java printer") }

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
