package org.vorpal.research.kex.ktype

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory

@InheritorOf("KexType")
@Serializable
sealed class KexInteger : KexType()

@InheritorOf("KexType")
@Serializable
object KexBool : KexInteger() {
    override val name: String
        get() = "bool"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.boolType
}

@InheritorOf("KexType")
@Serializable
object KexByte : KexInteger() {
    override val name: String
        get() = "byte"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.byteType
}

@InheritorOf("KexType")
@Serializable
object KexChar : KexInteger() {
    override val name: String
        get() = "char"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.charType
}

@InheritorOf("KexType")
@Serializable
object KexShort : KexInteger() {
    override val name: String
        get() = "short"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.shortType
}

@InheritorOf("KexType")
@Serializable
object KexInt : KexInteger() {
    override val name: String
        get() = "int"

    override val bitSize: Int
        get() = WORD

    override fun getKfgType(types: TypeFactory): Type = types.intType
}

@InheritorOf("KexType")
@Serializable
object KexLong : KexInteger() {
    override val name: String
        get() = "long"

    override val bitSize: Int
        get() = DWORD

    override fun getKfgType(types: TypeFactory): Type = types.longType
}
