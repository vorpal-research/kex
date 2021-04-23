package org.jetbrains.research.kex.ktype

import org.jetbrains.research.kthelper.defaultHashCode
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

@InheritorOf("KexType")
@Serializable
sealed class KexIntegral : KexType() {
    override fun hashCode() = defaultHashCode(name)
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

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.boolType
}

@InheritorOf("KexType")
@Serializable
class KexByte : KexIntegral() {
    override val name: String
        get() = "byte"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.byteType
}

@InheritorOf("KexType")
@Serializable
class KexChar : KexIntegral() {
    override val name: String
        get() = "char"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.charType
}

@InheritorOf("KexType")
@Serializable
class KexShort : KexIntegral() {
    override val name: String
        get() = "short"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.shortType
}

@InheritorOf("KexType")
@Serializable
class KexInt : KexIntegral() {
    override val name: String
        get() = "int"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.intType

    override fun hashCode() = defaultHashCode(name)
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

    override val bitsize: Int
        get() = DWORD

    override fun getKfgType(types: TypeFactory): Type = types.longType
}