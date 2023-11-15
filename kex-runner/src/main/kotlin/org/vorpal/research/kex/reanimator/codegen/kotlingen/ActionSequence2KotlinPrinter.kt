package org.vorpal.research.kex.reanimator.codegen.kotlingen

import org.vorpal.research.kex.ExecutionContext
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
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.jvm.kotlinFunction
import java.lang.reflect.Type as JType

// TODO: this is work of satan, refactor this damn thing
open class ActionSequence2KotlinPrinter(
    val ctx: ExecutionContext,
    final override val packageName: String,
    override val klassName: String
) : ActionSequencePrinter {
    private val printedStacks = mutableSetOf<String>()
    private val builder = KtBuilder(packageName)
    private val klass: KtBuilder.KtClass
    private val resolvedTypes = mutableMapOf<ActionSequence, ASType>()
    private val actualTypes = mutableMapOf<ActionSequence, ASType>()
    private var testCounter = 0
    lateinit var current: KtBuilder.KtFunction

    init {
        with(builder) {
            import("kotlin.Exception")
            import("kotlin.IllegalStateException")
            import("org.junit.Test")
            function("<T> unknown") {
                returnType = type("T")
                +"TODO()"
            }
        }
        klass = builder.run { klass(packageName, klassName) }
    }

    private fun buildMethodCall(
        method: org.vorpal.research.kfg.ir.Method, actionSequences: Parameters<ActionSequence>
    ): ActionSequence = when {
        method.isStatic -> TestCall("test${testCounter++}", method, null, actionSequences.arguments)
        method.isConstructor -> actionSequences.instance!!
        else -> TestCall("test${testCounter++}", method, actionSequences.instance, actionSequences.arguments)
    }

    override fun printActionSequence(
        testName: String,
        method: org.vorpal.research.kfg.ir.Method,
        actionSequences: Parameters<ActionSequence>
    ) {
        resolvedTypes.clear()
        actualTypes.clear()
        printedStacks.clear()
        val actionSequence = buildMethodCall(method, actionSequences)
        with(builder) {
            with(klass) {
                current = method(testName) {
                    returnType = unit
                    annotations += "Test"
                }
            }
        }
        resolveTypes(actionSequence)
        actionSequence.printAsKt()
    }

    override fun emit() = builder.toString()


    interface ASType {
        val nullable: Boolean

        fun isSubtype(other: ASType): Boolean
    }

    inner class ASStarProjection : ASType {
        override val nullable = true
        override fun isSubtype(other: ASType) = other is ASStarProjection
        override fun toString() = "*"
    }

    inner class ASClass(
        val type: Type,
        val typeParams: List<ASType> = emptyList(),
        override val nullable: Boolean = true
    ) : ASType {
        override fun isSubtype(other: ASType): Boolean = when (other) {
            is ASClass -> when {
                !type.isSubtypeOfCached(other.type) -> false
                typeParams.size != other.typeParams.size -> false
                typeParams.zip(other.typeParams).any { (a, b) -> !a.isSubtype(b) } -> false
                else -> !(!nullable && other.nullable)
            }

            is ASStarProjection -> true
            else -> false
        }

        override fun toString(): String {
            val typeParams = when (typeParams.isNotEmpty()) {
                true -> typeParams.joinToString(", ", prefix = "<", postfix = ">")
                else -> ""
            }
            return type.kotlinString + typeParams + if (nullable) "?" else ""
        }
    }

    inner class ASPrimaryArray(val element: ASType, override val nullable: Boolean = true) : ASType {
        override fun isSubtype(other: ASType): Boolean = when (other) {
            is ASPrimaryArray -> element.isSubtype(other.element) && !(!nullable && other.nullable)
            is ASStarProjection -> true
            else -> false
        }

        override fun toString() = "${element}Array" + if (nullable) "?" else ""
    }

    inner class ASArray(val element: ASType, override val nullable: Boolean = true) : ASType {
        override fun isSubtype(other: ASType): Boolean = when (other) {
            is ASArray -> when {
                !element.isSubtype(other.element) -> false
                !nullable && other.nullable -> false
                else -> true
            }

            is ASStarProjection -> true
            else -> false
        }

        override fun toString() = "Array<${element}>" + if (nullable) "?" else ""
    }

    private val KClassifier.asType: ASType
        get() = when (this) {
            is KClass<*> -> java.kex.getAsType(false)
            is KTypeParameter -> upperBounds.first().asType
            else -> unreachable { }
        }

    @Suppress("RecursivePropertyAccessor")
    val ASType.kfg: Type
        get() = when (this) {
            is ASClass -> type
            is ASArray -> ctx.types.getArrayType(element.kfg)
            is ASPrimaryArray -> ctx.types.getArrayType(element.kfg)
            else -> unreachable { }
        }

    private val KType.asType: ASType
        get() {
            val type = this.classifier!!.asType.kfg
            val args = this.arguments.map { it.type?.asType ?: ASStarProjection() }
            val nullability = this.isMarkedNullable
            return when (type) {
                is ArrayType -> when {
                    type.component.isPrimitive -> ASPrimaryArray(type.component.getAsType(false), nullability)
                    else -> ASArray(args.first(), nullability)
                }

                else -> ASClass(type, args, nullability)
            }
        }

    @Suppress("RecursivePropertyAccessor")
    private val JType.asType: ASType
        get() = when (this) {
            is java.lang.Class<*> -> when {
                this.isArray -> {
                    val element = this.componentType.asType
                    ASArray(element)
                }

                else -> ASClass(this.kex.getKfgType(ctx.types))
            }

            is ParameterizedType -> this.ownerType.asType
            is TypeVariable<*> -> this.bounds.first().asType
            is WildcardType -> this.upperBounds.first().asType
            else -> TODO()
        }

    private fun ASType.merge(requiredType: ASType): ASType = when {
        this is ASClass && requiredType is ASClass -> {
            val actualKlass = ctx.loader.loadClass(type)
            val requiredKlass = ctx.loader.loadClass(requiredType.type)
            if (requiredKlass.isAssignableFrom(actualKlass) && actualKlass.typeParameters.size == requiredKlass.typeParameters.size) {
                ASClass(type, requiredType.typeParams, false)
            } else TODO()
        }

        else -> TODO()
    }

    private fun ASType?.isAssignable(other: ASType) = this?.let { other.isSubtype(it) } ?: true

    private fun KexType.getAsType(nullable: Boolean = true) = this.getKfgType(ctx.types).getAsType(nullable)

    private fun Type.getAsType(nullable: Boolean = true): ASType = when (this) {
        is ArrayType -> when {
            this.component.isPrimitive -> ASPrimaryArray(component.getAsType(false), nullable)
            else -> ASArray(this.component.getAsType(nullable), nullable)
        }

        else -> ASClass(this, nullable = nullable)
    }

    private fun resolveTypes(actionSequence: ActionSequence) {
        if (actionSequence is ActionList) actionSequence.reversed().map { resolveTypes(it) }
        else if (actionSequence is TestCall) {
            actionSequence.instance?.let { resolveTypes(it) }
            actionSequence.args.forEach { resolveTypes(it) }
        }
    }

    private fun resolveTypes(constructor: Constructor<*>, args: List<ActionSequence>) =
        when {
            constructor.kotlinFunction != null -> {
                val params = constructor.kotlinFunction!!.parameters
                args.zip(params).forEach { (arg, param) ->
                    if (arg !in resolvedTypes) {
                        resolvedTypes[arg] = param.type.asType
                        resolveTypes(arg)
                    }
                }
            }

            else -> {
                val params = constructor.genericParameterTypes
                args.zip(params).forEach { (arg, param) ->
                    if (arg !in resolvedTypes) {
                        resolvedTypes[arg] = param.asType
                        resolveTypes(arg)
                    }
                }
            }
        }

    private fun resolveTypes(method: Method, args: List<ActionSequence>) =
        when {
            method.kotlinFunction != null -> {
                val params = method.kotlinFunction!!.parameters.drop(1)
                args.zip(params).forEach { (arg, param) ->
                    if (arg !in resolvedTypes) {
                        resolvedTypes[arg] = param.type.asType
                        resolveTypes(arg)
                    }
                }
            }

            else -> {
                val params = method.genericParameterTypes.toList()
                args.zip(params).forEach { (arg, param) ->
                    if (arg !in resolvedTypes) {
                        resolvedTypes[arg] = param.asType
                        resolveTypes(arg)
                    }
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

    private fun ActionSequence.printAsKt() {
        if (name in printedStacks) return
        printedStacks += name
        val statements = when (this) {
            is TestCall -> listOf(printTestCall(this))
            is UnknownSequence -> listOf(printUnknownSequence(this))
            is ActionList -> this.map { printApiCall(this, it) }
            is ReflectionList -> this.flatMap { printReflectionCall(this, it) }
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

    private val Class.kotlinString: String get() = this.asType.kotlinString

    @Suppress("RecursivePropertyAccessor")
    private val Type.kotlinString: String
        get() = when (this) {
            is NullType -> "null"
            is VoidType -> "Unit"
            is BoolType -> "Boolean"
            is ByteType -> "Byte"
            is ShortType -> "Short"
            is CharType -> "Char"
            is IntType -> "Int"
            is LongType -> "Long"
            is FloatType -> "Float"
            is DoubleType -> "Double"
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
                val klass = (this as ClassType).klass
                val name = klass.canonicalDesc.replace("$", ".")
                builder.import(name)
                klass.name.replace("$", ".")
            }
        }

    private val ActionSequence.stackName: String
        get() = when (this) {
            is PrimaryValue<*> -> asConstant
            is StringValue -> asConstant
            else -> name
        }

    private fun printApiCall(owner: ActionSequence, codeAction: CodeAction): String = when (codeAction) {
        is DefaultConstructorCall -> printDefaultConstructor(owner, codeAction)
        is ConstructorCall -> printConstructorCall(owner, codeAction)
        is ExternalConstructorCall -> printExternalConstructorCall(owner, codeAction)
        is MethodCall -> printMethodCall(owner, codeAction)
        is StaticMethodCall -> printStaticMethodCall(codeAction)
        is NewArray -> printNewArray(owner, codeAction)
        is ArrayWrite -> printArrayWrite(owner, codeAction)
        is FieldSetter -> printFieldSetter(owner, codeAction)
        is StaticFieldSetter -> printStaticFieldSetter(codeAction)
        is EnumValueCreation -> printEnumValueCreation(owner, codeAction)
        is StaticFieldGetter -> printStaticFieldGetter(owner, codeAction)
        is ClassConstantGetter -> printClassConstantGetter(owner, codeAction)
        is ArrayClassConstantGetter -> printArrayClassConstantGetter(owner, codeAction)
        is ExternalMethodCall -> TODO()
        is InnerClassConstructorCall -> TODO()
        is NewArrayWithInitializer -> TODO()
    }

    private fun printReflectionCall(owner: ActionSequence, reflectionCall: ReflectionCall): List<String> =
        when (reflectionCall) {
            is ReflectionArrayWrite -> printReflectionArrayWrite(owner, reflectionCall)
            is ReflectionNewArray -> printReflectionNewArray(owner, reflectionCall)
            is ReflectionNewInstance -> printReflectionNewInstance(owner, reflectionCall)
            is ReflectionSetField -> printReflectionSetField(owner, reflectionCall)
            is ReflectionSetStaticField -> printReflectionSetStaticField(owner, reflectionCall)
        }

    private val <T> PrimaryValue<T>.asConstant: String
        get() = when (val value = value) {
            null -> "null".also {
                actualTypes[this] = ASClass(ctx.types.nullType)
            }

            is Boolean -> "$value".also {
                actualTypes[this] = ASClass(ctx.types.boolType, nullable = false)
            }

            is Byte -> "${value}.toByte()".also {
                actualTypes[this] = ASClass(ctx.types.byteType, nullable = false)
            }

            is Char -> when (value) {
                in 'a'..'z' -> "'$value'"
                in 'A'..'Z' -> "'$value'"
                else -> "${value.code}.toChar()"
            }.also {
                actualTypes[this] = ASClass(ctx.types.charType, nullable = false)
            }

            is Short -> "${value}.toShort()".also {
                actualTypes[this] = ASClass(ctx.types.shortType, nullable = false)
            }

            is Int -> "$value".also {
                actualTypes[this] = ASClass(ctx.types.intType, nullable = false)
            }

            is Long -> "${value}L".also {
                actualTypes[this] = ASClass(ctx.types.longType, nullable = false)
            }

            is Float -> "${value}F".also {
                actualTypes[this] = ASClass(ctx.types.floatType, nullable = false)
            }

            is Double -> "$value".also {
                actualTypes[this] = ASClass(ctx.types.doubleType, nullable = false)
            }

            else -> unreachable { log.error("Unknown primary value $this") }
        }

    private val StringValue.asConstant: String
        get() {
            val escapedValue = value.map { KtBuilder.escapeCharIfNeeded(it) }.joinToString("")
            return "\"$escapedValue\"".also {
                actualTypes[this] = ASClass(ctx.types.nullType)
            }
        }

    private fun ActionSequence.cast(reqType: ASType?): String {
        val actualType = actualTypes[this] ?: return this.stackName
        return when {
            reqType.isAssignable(actualType) -> this.stackName
            else -> "${this.stackName} as $reqType"
        }
    }

    private fun ActionSequence.forceCastIfNull(reqType: ASType?): String = when (this.stackName) {
        "null" -> "${this.stackName} as $reqType"
        else -> this.cast(reqType)
    }

    private fun printDefaultConstructor(owner: ActionSequence, call: DefaultConstructorCall): String {
        val actualType = ASClass(call.klass.asType, nullable = false)
        return if (resolvedTypes[owner] != null) {
            val rest = resolvedTypes[owner]!!
            val type = actualType.merge(rest)
            actualTypes[owner] = type
            "val ${owner.name} = $type()"
        } else {
            actualTypes[owner] = actualType
            "val ${owner.name} = $actualType()"
        }
    }

    private fun printConstructorCall(owner: ActionSequence, call: ConstructorCall): String {
        call.args.forEach { it.printAsKt() }
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = ASClass(call.constructor.klass.asType, nullable = false)
        return if (resolvedTypes[owner] != null) {
            val rest = resolvedTypes[owner]!!
            val type = actualType.merge(rest)
            actualTypes[owner] = type
            "val ${owner.name} = $type($args)"
        } else {
            actualTypes[owner] = actualType
            "val ${owner.name} = $actualType($args)"
        }
    }

    private fun printExternalConstructorCall(owner: ActionSequence, call: ExternalConstructorCall): String {
        call.args.forEach { it.printAsKt() }
        val constructor = call.constructor
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        val actualType = ASClass(constructor.returnType)
        actualTypes[owner] = actualType
        return "val ${owner.name} = ${constructor.klass.kotlinString}.${constructor.name}($args)"
    }

    private fun printMethodCall(owner: ActionSequence, call: MethodCall): String {
        call.args.forEach { it.printAsKt() }
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return "${owner.name}.${method.name}($args)"
    }

    private fun printStaticMethodCall(call: StaticMethodCall): String {
        call.args.forEach { it.printAsKt() }
        val klass = call.method.klass
        val method = call.method
        val args = call.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return "${klass.kotlinString}.${method.name}($args)"
    }

    private fun printNewArray(owner: ActionSequence, call: NewArray): String {
        val newArray = when (val type = call.asArray.component) {
            is ClassType, is ArrayType -> {
                actualTypes[owner] = ASArray(type.getAsType(), false)
                "arrayOfNulls<${type.kotlinString}>"
            }

            else -> {
                actualTypes[owner] = call.asArray.getAsType(false)
                call.asArray.kotlinString
            }
        }
        return "val ${owner.name} = $newArray(${call.length.stackName})"
    }

    private fun printArrayWrite(owner: ActionSequence, call: ArrayWrite): String {
        call.value.printAsKt()
        val requiredType = when (val resT = resolvedTypes[owner] ?: actualTypes[owner]) {
            is ASArray -> resT.element
            is ASPrimaryArray -> resT.element
            else -> unreachable { }
        }
        return "${owner.name}[${call.index.stackName}] = ${call.value.cast(requiredType)}"
    }

    private fun printFieldSetter(owner: ActionSequence, call: FieldSetter): String {
        call.value.printAsKt()
        return "${owner.name}.${call.field.name} = ${call.value.stackName}"
    }

    private fun printStaticFieldSetter(call: StaticFieldSetter): String {
        call.value.printAsKt()
        return "${call.field.klass.kotlinString}.${call.field.name} = ${call.value.stackName}"
    }

    private fun printEnumValueCreation(owner: ActionSequence, call: EnumValueCreation): String {
        val actualType = call.klass.asType.getAsType(false)
        actualTypes[owner] = actualType
        return "val ${owner.name} = ${call.klass.kotlinString}.${call.name}"
    }

    private fun printStaticFieldGetter(owner: ActionSequence, call: StaticFieldGetter): String {
        val actualType = call.field.klass.asType.getAsType(false)
        actualTypes[owner] = actualType
        return "val ${owner.name} = ${call.field.klass.kotlinString}.${call.field.name}"
    }

    protected open fun printClassConstantGetter(owner: ActionSequence, call: ClassConstantGetter): String {
        val actualType = ASClass(ctx.types.classType)
        actualTypes[owner] = actualType
        return "val ${owner.name} = ${call.type.kotlinString}::class.java"
    }

    protected open fun printArrayClassConstantGetter(owner: ActionSequence, call: ArrayClassConstantGetter): String {
        call.elementType.printAsKt()
        val actualType = ASClass(ctx.types.classType)
        actualTypes[owner] = actualType
        return "val ${owner.name} =  = Array.newInstance(${call.elementType.stackName}, 0).javaClass"
    }

    private fun printTestCall(sequence: TestCall): String {
        sequence.instance?.printAsKt()
        sequence.args.forEach { it.printAsKt() }
        val callee = when (sequence.instance) {
            null -> sequence.test.klass.kotlinString
            else -> sequence.instance.name
        }
        val args = sequence.args.joinToString(", ") {
            it.forceCastIfNull(resolvedTypes[it])
        }
        return "$callee.${sequence.test.name}($args)"
    }

    private fun printUnknownSequence(seq: UnknownSequence): String {
        val type = seq.target.type.getAsType()
        actualTypes[seq] = type
        return "val ${seq.name} = unknown<$type>()"
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

}
