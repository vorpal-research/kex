package org.vorpal.research.kex.ktype

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory

@InheritorOf("KexType")
@Serializable
sealed class KexIntegral : KexType() {
    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        return true
    }
}

@InheritorOf("KexType")
@Serializable
class KexBool : KexIntegral() {
    override val name: String
        get() = "bool"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.boolType
}

@InheritorOf("KexType")
@Serializable
class KexByte : KexIntegral() {
    override val name: String
        get() = "byte"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.byteType
}

@InheritorOf("KexType")
@Serializable
class KexChar : KexIntegral() {
    override val name: String
        get() = "char"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.charType
}

@InheritorOf("KexType")
@Serializable
class KexShort : KexIntegral() {
    override val name: String
        get() = "short"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.shortType
}

@InheritorOf("KexType")
@Serializable
class KexInt : KexIntegral() {
    override val name: String
        get() = "int"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.intType

    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexInt) return false
        return true
    }
}

@InheritorOf("KexType")
@Serializable
class KexLong : KexIntegral() {
    override val name: String
        get() = "long"

    override val bitSize: Int
        get() = DWORD

    override fun getKfgType(types: TypeFactory): Type = types.longType
}