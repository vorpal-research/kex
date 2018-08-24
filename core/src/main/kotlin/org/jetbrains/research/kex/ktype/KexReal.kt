package org.jetbrains.research.kex.ktype

import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.type.Type

sealed class KexReal : KexType()

object KexFloat : KexReal() {
    override val name: String
        get() = "float"

    override val bitsize: Int
        get() = KexType.WORD

    override val kfgType: Type
        get() = TF.floatType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}

object KexDouble : KexReal() {
    override val name: String
        get() = "double"

    override val bitsize: Int
        get() = KexType.DWORD

    override val kfgType: Type
        get() = TF.doubleType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}