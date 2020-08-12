package org.jetbrains.research.kex.generator.descriptor

import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.util.allFields
import org.jetbrains.research.kex.util.kex
import java.util.*

class Object2DescriptorConverter : DescriptorBuilder() {
    private val objectToDescriptor = IdentityHashMap<Any, Descriptor>()

    fun convert(any: Any?): Descriptor = when (any) {
        null -> `null`
        in objectToDescriptor -> objectToDescriptor[any]!!
        is Boolean -> const(any)
        is Byte -> const(any)
        is Char -> const(any.toByte())
        is Short -> const(any)
        is Int -> const(any)
        is Long -> const(any)
        is Float -> const(any)
        is Double -> const(any)
        is Array<*> -> array(any)
        else -> `object`(any)
    }

    fun `object`(any: Any): Descriptor {
        val klass = any.javaClass
        val kexClass = klass.kex
        val result = `object`(kexClass)
        objectToDescriptor[any] = result
        for (field in klass.allFields) {
            field.isAccessible = true

            val name = field.name
            val type = field.type.kex

            val actualValue = field.get(any)
            val descriptorValue = convert(actualValue)
            result[name to type] = descriptorValue
        }
        return result
    }

    fun array(array: Array<*>): Descriptor {
        val elementType = array.javaClass.componentType.kex
        val arrayType = KexArray(elementType)
        val result = array(array.size, arrayType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element)
            result[index] = elementDescriptor
        }
        return result
    }
}

val Any?.descriptor get() = Object2DescriptorConverter().convert(this)

val Iterable<Any?>.descriptors get(): Iterable<Any?> {
    val converter = Object2DescriptorConverter()
    return this.map { converter.convert(it) }
}