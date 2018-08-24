package org.jetbrains.research.kex.ktype

import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.Type

sealed class KexPointer(open val memspace: Int = defaultMemspace) : KexType() {
    companion object {
        const val defaultMemspace = 0
    }

    override val bitsize: Int
        get() = KexType.WORD

    abstract fun withMemspace(memspace: Int): KexPointer
}

class KexClass(val `class`: Class, memspace: Int = defaultMemspace) : KexPointer(memspace) {
    override val name: String
        get() = `class`.fullname

    override val kfgType: Type
        get() = TF.getRefType(`class`)

    override fun withMemspace(memspace: Int) = KexClass(`class`, memspace)

    override fun hashCode() = defaultHashCode(`class`)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexClass

        if (`class` != other.`class`) return false

        return true
    }
}

class KexArray(val element: KexType, memspace: Int = defaultMemspace) : KexPointer(memspace) {
    override val name: String
        get() = "$element[]"

    override val kfgType: Type
        get() = TF.getArrayType(element.kfgType)

    override fun withMemspace(memspace: Int) = KexArray(element, memspace)

    override fun hashCode() = defaultHashCode(element)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexArray

        if (element != other.element) return false

        return true
    }
}

class KexReference(val referencable: KexType, memspace: Int = defaultMemspace) : KexPointer(memspace) {
    override val name: String
        get() = "&($referencable)"

    override val kfgType: Type
        get() = referencable.kfgType

    override fun withMemspace(memspace: Int) = KexReference(referencable, memspace)

    override fun hashCode() = defaultHashCode(referencable)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexReference

        if (referencable != other.referencable) return false

        return true
    }
}

object KexNull : KexPointer() {
    override val name: String
        get() = "null"

    override val kfgType: Type
        get() = TF.nullType

    override fun withMemspace(memspace: Int) = this

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}