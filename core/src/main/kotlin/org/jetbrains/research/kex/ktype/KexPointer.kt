package org.jetbrains.research.kex.ktype

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

@InheritorOf("KexType")
@Serializable
sealed class KexPointer : KexType() {
    abstract val memspace: Int

    companion object {
        const val defaultMemspace = 0
    }

    override val bitsize: Int
        get() = WORD

    abstract fun withMemspace(memspace: Int): KexPointer
}

@InheritorOf("KexType")
@Serializable
class KexClass(val `class`: String, override val memspace: Int = defaultMemspace) : KexPointer() {
    override val name: String
        get() = `class`

    fun kfgClass(types: TypeFactory) = types.cm.getByName(`class`)

    override fun getKfgType(types: TypeFactory): Type = types.getRefType(`class`)
    fun getKfgClass(types: TypeFactory): Class = types.cm.getByName(`class`)

    override fun withMemspace(memspace: Int) = KexClass(`class`, memspace)

    override fun hashCode() = defaultHashCode(`class`)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexClass

        return `class` == other.`class` && memspace == other.memspace
    }
}

@InheritorOf("KexType")
@Serializable
class KexArray(
        val element: KexType,
        override val memspace: Int = defaultMemspace) : KexPointer() {
    override val name: String
        get() = "$element[]"

    override fun getKfgType(types: TypeFactory): Type = types.getArrayType(element.getKfgType(types))

    override fun withMemspace(memspace: Int) = KexArray(element, memspace)

    override fun hashCode() = defaultHashCode(element)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexArray

        return element == other.element && memspace == other.memspace
    }
}

@InheritorOf("KexType")
@Serializable
class KexReference(
        val reference: KexType,
        override val memspace: Int = defaultMemspace) : KexPointer() {
    override val name: String
        get() = "&($reference)"

    override fun getKfgType(types: TypeFactory): Type = reference.getKfgType(types)

    override fun withMemspace(memspace: Int) = KexReference(reference, memspace)

    override fun hashCode() = defaultHashCode(reference)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexReference

        return reference == other.reference && memspace == other.memspace
    }
}

@InheritorOf("KexType")
@Serializable
class KexNull : KexPointer() {
    override val name: String
        get() = "null"

    override val memspace = defaultMemspace

    override fun getKfgType(types: TypeFactory): Type = types.nullType

    override fun withMemspace(memspace: Int) = this

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexNull) return false
        return true
    }
}