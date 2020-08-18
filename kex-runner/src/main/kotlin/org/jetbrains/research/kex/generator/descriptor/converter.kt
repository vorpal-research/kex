package org.jetbrains.research.kex.generator.descriptor

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.util.allFields
import org.jetbrains.research.kex.util.isStatic
import org.jetbrains.research.kex.util.kex
import java.util.*

private val maxGenerationDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxGenerationDepth", 100) }

class Object2DescriptorConverter : DescriptorBuilder() {
    private val objectToDescriptor = IdentityHashMap<Any, Descriptor>()

    fun convert(any: Any?, depth: Int = 0): Descriptor = when (any) {
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
        is BooleanArray -> array(any.toTypedArray(), depth)
        is ByteArray -> array(any.toTypedArray(), depth)
        is CharArray -> array(any.toTypedArray(), depth)
        is ShortArray -> array(any.toTypedArray(), depth)
        is IntArray -> array(any.toTypedArray(), depth)
        is LongArray -> array(any.toTypedArray(), depth)
        is FloatArray -> array(any.toTypedArray(), depth)
        is DoubleArray -> array(any.toTypedArray(), depth)
        is Array<*> -> array(any, depth)
        else -> `object`(any, depth)
    }

    fun `object`(any: Any, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val klass = any.javaClass
        val kexClass = klass.kex as KexClass
        val result = `object`(kexClass)
        objectToDescriptor[any] = result
        for (field in klass.allFields) {
            if (field.isStatic) continue
            field.isAccessible = true

            val name = field.name
            val type = field.type.kex

            val actualValue = field.get(any)
            val descriptorValue = convert(actualValue, depth + 1)
            result[name to type] = descriptorValue
        }
        return result
    }

    fun array(array: Array<*>, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = array.javaClass.componentType.kex
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }
}

val Any?.descriptor get() = Object2DescriptorConverter().convert(this)

val Iterable<Any?>.descriptors get() = Object2DescriptorConverter().let {
    this.map { any -> it.convert(any) }
}