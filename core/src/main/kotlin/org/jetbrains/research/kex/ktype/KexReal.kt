package org.jetbrains.research.kex.ktype

import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

sealed class KexReal : KexType()

object KexFloat : KexReal() {
    override val name: String
        get() = "float"

    override val bitsize: Int
        get() = KexType.WORD

    override fun getKfgType(types: TypeFactory): Type = types.floatType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}

object KexDouble : KexReal() {
    override val name: String
        get() = "double"

    override val bitsize: Int
        get() = KexType.DWORD

    override fun getKfgType(types: TypeFactory): Type = types.doubleType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}