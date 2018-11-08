package org.jetbrains.research.kex.ktype

import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

sealed class KexIntegral : KexType()

object KexBool : KexIntegral() {
    override val name: String
        get() = "bool"

    override val bitsize: Int
        get() = KexType.WORD

    override fun getKfgType(types: TypeFactory): Type = types.boolType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}

object KexByte : KexIntegral() {
    override val name: String
        get() = "byte"

    override val bitsize: Int
        get() = KexType.WORD

    override fun getKfgType(types: TypeFactory): Type = types.byteType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}

object KexChar : KexIntegral() {
    override val name: String
        get() = "char"

    override val bitsize: Int
        get() = KexType.WORD

    override fun getKfgType(types: TypeFactory): Type = types.charType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}

object KexShort : KexIntegral() {
    override val name: String
        get() = "short"

    override val bitsize: Int
        get() = KexType.WORD

    override fun getKfgType(types: TypeFactory): Type = types.shortType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}

object KexInt : KexIntegral() {
    override val name: String
        get() = "int"

    override val bitsize: Int
        get() = KexType.WORD

    override fun getKfgType(types: TypeFactory): Type = types.intType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}

object KexLong : KexIntegral() {
    override val name: String
        get() = "long"

    override val bitsize: Int
        get() = KexType.DWORD

    override fun getKfgType(types: TypeFactory): Type = types.longType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}