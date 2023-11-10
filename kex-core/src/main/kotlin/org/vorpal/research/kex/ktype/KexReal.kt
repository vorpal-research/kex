package org.vorpal.research.kex.ktype

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory

@InheritorOf("KexType")
@Serializable
sealed class KexReal : KexType()

@InheritorOf("KexType")
@Serializable
object KexFloat : KexReal() {
    override val name: String
        get() = "float"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.floatType

    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KexFloat
    }
}

@InheritorOf("KexType")
@Serializable
object KexDouble : KexReal() {
    override val name: String
        get() = "double"

    override val bitSize: Int
        get() = DWORD

    override fun getKfgType(types: TypeFactory): Type = types.doubleType

    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KexDouble
    }
}
