package org.jetbrains.research.kex.ktype

import org.jetbrains.research.kthelper.defaultHashCode
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

@InheritorOf("KexType")
@Serializable
sealed class KexReal : KexType()

@InheritorOf("KexType")
@Serializable
class KexFloat : KexReal() {
    override val name: String
        get() = "float"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.floatType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexFloat) return false
        return true
    }
}

@InheritorOf("KexType")
@Serializable
class KexDouble : KexReal() {
    override val name: String
        get() = "double"

    override val bitsize: Int
        get() = DWORD

    override fun getKfgType(types: TypeFactory): Type = types.doubleType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexDouble) return false
        return true
    }
}