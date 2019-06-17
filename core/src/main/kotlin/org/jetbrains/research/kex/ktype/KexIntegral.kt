package org.jetbrains.research.kex.ktype

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

@InheritorOf("KexType")
@Serializable
sealed class KexIntegral : KexType()

@InheritorOf("KexType")
@Serializable
class KexBool : KexIntegral() {
    override val name: String
        get() = "bool"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.boolType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexBool) return false
        return true
    }
}

@InheritorOf("KexType")
@Serializable
class KexByte : KexIntegral() {
    override val name: String
        get() = "byte"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.byteType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexByte) return false
        return true
    }
}

@InheritorOf("KexType")
@Serializable
class KexChar : KexIntegral() {
    override val name: String
        get() = "char"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.charType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexChar) return false
        return true
    }
}

@InheritorOf("KexType")
@Serializable
class KexShort : KexIntegral() {
    override val name: String
        get() = "short"

    override val bitsize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.shortType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexShort) return false
        return true
    }
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

@Serializable
class KexLong : KexIntegral() {
    override val name: String
        get() = "long"

    override val bitsize: Int
        get() = DWORD

    override fun getKfgType(types: TypeFactory): Type = types.longType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexLong) return false
        return true
    }
}