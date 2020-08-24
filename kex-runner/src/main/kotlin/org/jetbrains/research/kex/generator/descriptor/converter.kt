package org.jetbrains.research.kex.generator.descriptor

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
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
        is Char -> const(any)
        is Short -> const(any)
        is Int -> const(any)
        is Long -> const(any)
        is Float -> const(any)
        is Double -> const(any)
        is BooleanArray -> booleanArray(any, depth)
        is ByteArray -> byteArray(any, depth)
        is CharArray -> charArray(any, depth)
        is ShortArray -> shortArray(any, depth)
        is IntArray -> intArray(any, depth)
        is LongArray -> longArray(any, depth)
        is FloatArray -> floatArray(any, depth)
        is DoubleArray -> doubleArray(any, depth)
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

    fun booleanArray(array: BooleanArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexBool()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun byteArray(array: ByteArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexByte()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun charArray(array: CharArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexChar()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun shortArray(array: ShortArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexShort()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun intArray(array: IntArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexInt()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun longArray(array: LongArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexLong()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun floatArray(array: FloatArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexFloat()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convert(element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun doubleArray(array: DoubleArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexDouble()
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