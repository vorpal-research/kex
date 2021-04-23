package org.jetbrains.research.kex.util

import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.toInt
import org.jetbrains.research.kthelper.tryOrNull
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Reference
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

val KType.isNonNullable get() = this.isMarkedNullable.not()

fun KexType.isNonNullable(kType: KType) = when (this) {
    is KexPointer -> kType.isNonNullable
    else -> false
}

fun KexType.isElementNullable(kType: KType) = when (this) {
    is KexArray -> when {
        kType.arguments.isEmpty() -> false
        else -> kType.arguments[0].type?.isMarkedNullable ?: true
    }
    else -> false
}

private fun trimClassName(name: String) = buildString {
    val actualName = name.split(" ").last()
    val filtered = actualName.dropWhile { it == '[' }.removeSuffix(";")
    append(actualName.takeWhile { it == '[' })
    append(filtered.dropWhile { it == 'L' })
}

val Type.trimmedName
    get() = when (this) {
        is Reference -> trimClassName(this.canonicalDesc)
        else -> this.name
    }
val Class<*>.trimmedName get() = trimClassName(this.toString())

infix fun KFunction<*>.eq(method: Method): Boolean {
    val parameters = this.parameters.drop(method.isAbstract.not().toInt())

    val name = `try` { this.javaMethod?.name }.getOrDefault(this.name)
    return name == method.name && parameters.zip(method.argTypes).fold(true) { acc, pair ->
        val type = tryOrNull { pair.first.type.jvmErasure.java }
        acc && type?.trimmedName == pair.second.trimmedName
    }
}

val KClass<*>.allFunctions
    get() = `try` {
        constructors +
                staticFunctions +
                declaredMemberFunctions +
                declaredMemberExtensionFunctions +
                declaredMemberProperties.map { it.getter } +
                declaredMemberExtensionProperties.map { it.getter } +
                declaredMemberProperties.filterIsInstance<KMutableProperty<*>>().map { it.setter } +
                declaredMemberExtensionProperties.filterIsInstance<KMutableProperty<*>>().map { it.setter } +
                staticProperties.map { it.getter } +
                staticProperties.filterIsInstance<KMutableProperty<*>>().map { it.setter }
    }.getOrElse(::listOf)

fun KClass<*>.find(method: Method) = allFunctions.find { it eq method }

fun ClassLoader.loadKClass(type: Type) = this.loadClass(type).kotlin
fun ClassLoader.loadKClass(tf: TypeFactory, type: KexType) = this.loadClass(tf, type).kotlin
fun ClassLoader.loadKClass(klass: org.jetbrains.research.kfg.ir.Class) = this.loadKClass(klass.type)

fun KClass<*>.getKFunction(method: Method): KFunction<*>? {
    val queue = queueOf(this)
    while (queue.isNotEmpty()) {
        val klass = queue.poll()
        when (val kFunction = klass.find(method)) {
            null -> {
                val supertypes = `try` { klass.supertypes }.getOrElse(::listOf)
                queue.addAll(supertypes.map { it.classifier }.filterIsInstance<KClass<*>>())
            }
            else -> return kFunction
        }
    }
    return null
}

fun KClass<*>.getKProperty(field: Field) =
        `try` { this.declaredMemberProperties }
                .map { klass -> klass.find { it.name == field.name } }
                .getOrNull()
