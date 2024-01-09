@file:Suppress("unused")

package org.vorpal.research.kex.descriptor

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kex.util.allFields
import org.vorpal.research.kex.util.isStatic
import org.vorpal.research.kex.util.kex
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.util.*

private val maxGenerationDepth by lazy {
    kexConfig.getIntValue("reanimator", "maxConversionDepth", 10)
}
private val maxArrayLength by lazy {
    kexConfig.getIntValue("reanimator", "maxArrayLength", 10_000)
}


class Object2DescriptorConverter : DescriptorBuilder() {
    private val objectToDescriptor = IdentityHashMap<Any, Descriptor>()

    fun type(any: Any?): KexType {
        if (any == null) return KexNull()

        return when (any.javaClass) {
            Boolean::class.javaObjectType -> KexClass(SystemTypeNames.booleanClass)
            Byte::class.javaObjectType -> KexClass(SystemTypeNames.byteClass)
            Char::class.javaObjectType -> KexClass(SystemTypeNames.charClass)
            Short::class.javaObjectType -> KexClass(SystemTypeNames.shortClass)
            Int::class.javaObjectType -> KexClass(SystemTypeNames.integerClass)
            Long::class.javaObjectType -> KexClass(SystemTypeNames.longClass)
            Float::class.javaObjectType -> KexClass(SystemTypeNames.floatClass)
            Double::class.javaObjectType -> KexClass(SystemTypeNames.doubleClass)
            else -> when (any) {
                is Boolean -> KexBool
                is Byte -> KexByte
                is Char -> KexChar
                is Short -> KexShort
                is Int -> KexInt
                is Long -> KexLong
                is Float -> KexFloat
                is Double -> KexDouble
                is BooleanArray -> KexBool.asArray()
                is ByteArray -> KexByte.asArray()
                is CharArray -> KexChar.asArray()
                is ShortArray -> KexShort.asArray()
                is IntArray -> KexInt.asArray()
                is LongArray -> KexLong.asArray()
                is FloatArray -> KexFloat.asArray()
                is DoubleArray -> KexDouble.asArray()
                is Array<*> -> any.javaClass.componentType.kex.asArray()
                is String -> KexString()
                else -> any.javaClass.kex
            }
        }
    }

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

    private fun convertElement(type: Class<*>, any: Any?, depth: Int): Descriptor = if (type.isPrimitive) {
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

    private fun booleanWrapper(any: Boolean): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.booleanClass)
        val result = `object`(wrapperClass)
        result["value" to KexBool] = const(any)
        return result
    }

    private fun byteWrapper(any: Byte): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.byteClass)
        val result = `object`(wrapperClass)
        result["value" to KexByte] = const(any)
        return result
    }

    private fun charWrapper(any: Char): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.charClass)
        val result = `object`(wrapperClass)
        result["value" to KexChar] = const(any)
        return result
    }

    private fun shortWrapper(any: Short): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.shortClass)
        val result = `object`(wrapperClass)
        result["value" to KexShort] = const(any)
        return result
    }

    private fun intWrapper(any: Int): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.integerClass)
        val result = `object`(wrapperClass)
        result["value" to KexInt] = const(any)
        return result
    }

    private fun longWrapper(any: Long): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.longClass)
        val result = `object`(wrapperClass)
        result["value" to KexLong] = const(any)
        return result
    }

    private fun floatWrapper(any: Float): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.floatClass)
        val result = `object`(wrapperClass)
        result["value" to KexFloat] = const(any)
        return result
    }

    private fun doubleWrapper(any: Double): Descriptor {
        val wrapperClass = KexClass(SystemTypeNames.doubleClass)
        val result = `object`(wrapperClass)
        result["value" to KexDouble] = const(any)
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
            if (field.name == "hashCode") {
                continue
            }

            val descriptorValue = convertElement(field.type, actualValue, depth + 1)
            result[name to type] = descriptorValue
        }
        return result
    }

    fun string(any: String, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val stringType = KexClass(SystemTypeNames.stringClass)
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
            if (index > maxArrayLength) break
            val elementDescriptor = convertElement(array.javaClass.componentType, element, depth + 1)
            result[index] = elementDescriptor
        }
        return result
    }

    private fun booleanArray(array: BooleanArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexBool)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    private fun byteArray(array: ByteArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexByte)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    private fun charArray(array: CharArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexChar)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    private fun shortArray(array: ShortArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexShort)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    private fun intArray(array: IntArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexInt)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    private fun longArray(array: LongArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexLong)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    private fun floatArray(array: FloatArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexFloat)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    private fun doubleArray(array: DoubleArray, depth: Int): Descriptor {
        if (depth > maxGenerationDepth) return `null`

        val result = array(array.size, KexDouble)
        for ((index, element) in array.withIndex()) {
            if (index > maxArrayLength) break
            result[index] = const(element)
        }
        return result
    }

    val Any?.descriptor get() = convert(this)
}

fun convertToDescriptor(any: Any?) = Object2DescriptorConverter().convert(any)
fun convertToDescriptors(iterable: Iterable<Any?>) = Object2DescriptorConverter().let { converter ->
    iterable.map { converter.convert(it) }
}
