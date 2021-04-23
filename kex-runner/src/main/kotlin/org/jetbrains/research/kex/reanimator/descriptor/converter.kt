package org.jetbrains.research.kex.reanimator.descriptor

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.util.allFields
import org.jetbrains.research.kex.util.isStatic
import org.jetbrains.research.kex.util.kex
import java.util.*

private val maxGenerationDepth by lazy {
    kexConfig.getIntValue("apiGeneration", "maxConversionDepth", 10)
}

class Object2DescriptorConverter : DescriptorBuilder() {
    private val objectToDescriptor = IdentityHashMap<Any, Descriptor>()

    fun convert(any: Any?, depth: Int = 0): Descriptor {
        if (any == null) return `null`
        if (any in objectToDescriptor) return objectToDescriptor[any]!!

        return when (any.javaClass) {
            Boolean::class.javaObjectType -> booleanWrapper(any as Boolean)
            Byte::class.javaObjectType -> byteWrapper(any as Byte)
            Char::class.javaObjectType -> charWrapper(any as Char)
            Short::class.javaObjectType -> shortWrapper(any as Short)
            Int::class.javaObjectType -> intWrapper(any as Int)
            Long::class.javaObjectType -> longWrapper(any as Long)
            Float::class.javaObjectType -> floatWrapper(any as Float)
            Double::class.javaObjectType -> doubleWrapper(any as Double)
            else -> when (any) {
                is Boolean -> const(any)
                is Byte -> const(any)
                is Char -> const(any)
                is Short -> const(any)
                is Int -> const(any)
                is Long -> const(any)
                is Float -> const(any)
                is Double -> const(any)
                is BooleanArray -> booleanArray(any, depth + 1)
                is ByteArray -> byteArray(any, depth + 1)
                is CharArray -> charArray(any, depth + 1)
                is ShortArray -> shortArray(any, depth + 1)
                is IntArray -> intArray(any, depth + 1)
                is LongArray -> longArray(any, depth + 1)
                is FloatArray -> floatArray(any, depth + 1)
                is DoubleArray -> doubleArray(any, depth + 1)
                is Array<*> -> array(any, depth + 1)
                is String -> string(any, depth + 1)
                else -> `object`(any, depth + 1)
            }
        }
    }

    fun convertElement(type: Class<*>, any: Any?, depth: Int): Descriptor = if (type.isPrimitive) {
        when (any) {
            is Boolean -> const(any)
            is Byte -> const(any)
            is Char -> const(any)
            is Short -> const(any)
            is Int -> const(any)
            is Long -> const(any)
            is Float -> const(any)
            is Double -> const(any)
            else -> unreachable { log.error("Unknown primitive type $any") }
        }
    } else {
        convert(any, depth)
    }

    fun booleanWrapper(any: Boolean): Descriptor {
        val wrapperClass = KexClass("java/lang/Boolean")
        val result = `object`(wrapperClass)
        result["value" to KexBool()] = const(any)
        return result
    }

    fun byteWrapper(any: Byte): Descriptor {
        val wrapperClass = KexClass("java/lang/Byte")
        val result = `object`(wrapperClass)
        result["value" to KexByte()] = const(any)
        return result
    }

    fun charWrapper(any: Char): Descriptor {
        val wrapperClass = KexClass("java/lang/Character")
        val result = `object`(wrapperClass)
        result["value" to KexChar()] = const(any)
        return result
    }

    fun shortWrapper(any: Short): Descriptor {
        val wrapperClass = KexClass("java/lang/Short")
        val result = `object`(wrapperClass)
        result["value" to KexShort()] = const(any)
        return result
    }

    fun intWrapper(any: Int): Descriptor {
        val wrapperClass = KexClass("java/lang/Integer")
        val result = `object`(wrapperClass)
        result["value" to KexInt()] = const(any)
        return result
    }

    fun longWrapper(any: Long): Descriptor {
        val wrapperClass = KexClass("java/lang/Long")
        val result = `object`(wrapperClass)
        result["value" to KexLong()] = const(any)
        return result
    }

    fun floatWrapper(any: Float): Descriptor {
        val wrapperClass = KexClass("java/lang/Float")
        val result = `object`(wrapperClass)
        result["value" to KexFloat()] = const(any)
        return result
    }

    fun doubleWrapper(any: Double): Descriptor {
        val wrapperClass = KexClass("java/lang/Double")
        val result = `object`(wrapperClass)
        result["value" to KexDouble()] = const(any)
        return result
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
            if (field.name == "hashCode" && any.hashCode() == actualValue) {
                continue
            }

            val descriptorValue = convertElement(field.type, actualValue, depth + 1)
            result[name to type] = descriptorValue
        }
        return result
    }

    fun string(any: String, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val stringType = KexClass("java/lang/String")
        val klass = any.javaClass
        val result = `object`(stringType)
        val field = klass.getDeclaredField("value")
        field.isAccessible = true

        val name = field.name
        val type = field.type.kex

        val actualValue = field.get(any)
        val descriptorValue = convertElement(field.type, actualValue, depth + 1)
        result[name to type] = descriptorValue

        return result
    }

    fun array(array: Array<*>, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = array.javaClass.componentType.kex
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            val elementDescriptor = convertElement(array.javaClass.componentType, element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    fun booleanArray(array: BooleanArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexBool()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }

    fun byteArray(array: ByteArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexByte()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }

    fun charArray(array: CharArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexChar()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }

    fun shortArray(array: ShortArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexShort()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }

    fun intArray(array: IntArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexInt()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }

    fun longArray(array: LongArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexLong()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }

    fun floatArray(array: FloatArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexFloat()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }

    fun doubleArray(array: DoubleArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val elementType = KexDouble()
        val result = array(array.size, elementType)
        for ((index, element) in array.withIndex()) {
            result[index] = const(element)
        }
        return result
    }
}

val Any?.descriptor get() = Object2DescriptorConverter().convert(this)

val Iterable<Any?>.descriptors get() = Object2DescriptorConverter().let {
    this.map { any -> it.convert(any) }
}