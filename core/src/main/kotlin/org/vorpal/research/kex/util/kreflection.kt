package org.vorpal.research.kex.util

import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.Reference
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.toInt
import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberExtensionFunctions
import kotlin.reflect.full.declaredMemberExtensionProperties
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.staticProperties
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

val KType.isNonNullable get() = when {
    this.isMarkedNullable -> false
    this.toString().endsWith("!") -> false // THIS IS FUCKED UP
    else -> true
}

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
    }.getOrElse { emptyList() }

fun KClass<*>.find(method: Method) = allFunctions.find { it eq method }

fun ClassLoader.loadKClass(type: Type) = this.loadClass(type).kotlin
fun ClassLoader.loadKClass(tf: TypeFactory, type: KexType) = this.loadClass(tf, type).kotlin
fun ClassLoader.loadKClass(klass: org.vorpal.research.kfg.ir.Class) = this.loadKClass(klass.asType)

fun KClass<*>.getKFunction(method: Method): KFunction<*>? {
    val queue = queueOf(this)
    while (queue.isNotEmpty()) {
        val klass = queue.poll()
        when (val kFunction = klass.find(method)) {
            null -> {
                val supertypes = `try` { klass.supertypes }.getOrElse { emptyList() }
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
