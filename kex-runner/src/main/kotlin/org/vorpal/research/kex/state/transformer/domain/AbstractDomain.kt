package org.vorpal.research.kex.state.transformer.domain

import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kthelper.and
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.compareTo
import org.vorpal.research.kthelper.div
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.minus
import org.vorpal.research.kthelper.or
import org.vorpal.research.kthelper.plus
import org.vorpal.research.kthelper.rem
import org.vorpal.research.kthelper.shl
import org.vorpal.research.kthelper.shr
import org.vorpal.research.kthelper.times
import org.vorpal.research.kthelper.toBoolean
import org.vorpal.research.kthelper.toInt
import org.vorpal.research.kthelper.ushr
import org.vorpal.research.kthelper.xor


sealed interface AbstractDomainValue {
    fun join(other: AbstractDomainValue): AbstractDomainValue
    fun meet(other: AbstractDomainValue): AbstractDomainValue

    fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun cast(type: KexType): AbstractDomainValue {
        return unreachable { log.error("$this cannot be cast to $type") }
    }

    fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue
    fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue
}

object Top : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun meet(other: AbstractDomainValue): AbstractDomainValue = other

    override fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue = Top

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = Top

    override fun cast(type: KexType): AbstractDomainValue = Top

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = Top
}

object Bottom : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = other
    override fun meet(other: AbstractDomainValue): AbstractDomainValue = Bottom

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = Top
}

sealed interface ConstantAbstractDomainValue : AbstractDomainValue

@JvmInline
value class ConstantDomainValue<T : Number>(val value: T) : ConstantAbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is ConstantDomainValue<*> -> if (value == other.value) this else Top
        is Bottom -> this
        else -> Top
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is ConstantDomainValue<*> -> if (value == other.value) this else Bottom
        is Top -> this
        else -> Bottom
    }

    override fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is ConstantDomainValue<*> -> when (opcode) {
            BinaryOpcode.ADD -> ConstantDomainValue(value + other.value)
            BinaryOpcode.SUB -> ConstantDomainValue(value - other.value)
            BinaryOpcode.MUL -> ConstantDomainValue(value * other.value)
            BinaryOpcode.DIV -> ConstantDomainValue(value / other.value)
            BinaryOpcode.REM -> ConstantDomainValue(value % other.value)
            BinaryOpcode.SHL -> ConstantDomainValue(value.shl(other.value.toInt()))
            BinaryOpcode.SHR -> ConstantDomainValue(value.shr(other.value.toInt()))
            BinaryOpcode.USHR -> ConstantDomainValue(value.ushr(other.value.toInt()))
            BinaryOpcode.AND -> ConstantDomainValue(value and other.value)
            BinaryOpcode.OR -> ConstantDomainValue(value or other.value)
            BinaryOpcode.XOR -> ConstantDomainValue(value xor other.value)
        }

        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is ConstantDomainValue<*> -> when (opcode) {
            CmpOpcode.EQ -> ConstantDomainValue((value == other.value).toInt())
            CmpOpcode.NEQ -> ConstantDomainValue((value != other.value).toInt())
            CmpOpcode.LT -> ConstantDomainValue((value < other.value).toInt())
            CmpOpcode.GT -> ConstantDomainValue((value > other.value).toInt())
            CmpOpcode.LE -> ConstantDomainValue((value <= other.value).toInt())
            CmpOpcode.GE -> ConstantDomainValue((value >= other.value).toInt())
            CmpOpcode.CMP -> ConstantDomainValue(value.compareTo(other.value))
            CmpOpcode.CMPG -> ConstantDomainValue(value.compareTo(other.value))
            CmpOpcode.CMPL -> ConstantDomainValue(value.compareTo(other.value))
        }

        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun cast(type: KexType): AbstractDomainValue = when (type) {
        is KexBool -> ConstantDomainValue(value.toBoolean().toInt())
        is KexByte -> ConstantDomainValue(value.toByte())
        is KexChar -> ConstantDomainValue(value.toChar().code)
        is KexShort -> ConstantDomainValue(value.toShort())
        is KexInt -> ConstantDomainValue(value.toInt())
        is KexLong -> ConstantDomainValue(value.toLong())
        is KexFloat -> ConstantDomainValue(value.toFloat())
        is KexDouble -> ConstantDomainValue(value.toDouble())
        else -> unreachable { log.error("$this cannot be cast to $type") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> SatDomainValue
        is ConstantDomainValue<*> -> if (value == other.value) SatDomainValue else UnsatDomainValue
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> SatDomainValue
        is ConstantDomainValue<*> -> if (value != other.value) SatDomainValue else UnsatDomainValue
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }
}

sealed interface NullityAbstractDomainValue : AbstractDomainValue {
    override fun cast(type: KexType): AbstractDomainValue = this
}

object NonNullableDomainValue : NullityAbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NonNullableDomainValue -> NonNullableDomainValue
        is Bottom -> NonNullableDomainValue
        else -> Top
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NonNullableDomainValue -> NonNullableDomainValue
        is Top -> this
        else -> Bottom
    }

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> when (opcode) {
            CmpOpcode.EQ -> ConstantDomainValue(false.toInt())
            CmpOpcode.NEQ -> ConstantDomainValue(true.toInt())
            else -> unreachable { log.error("$this does not support operation $opcode with $other") }
        }

        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> UnsatDomainValue
        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> SatDomainValue
        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }
}

object NullDomainValue : NullityAbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NullDomainValue -> NullDomainValue
        is Bottom -> NullDomainValue
        else -> NullableDomainValue
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NullDomainValue -> NullDomainValue
        is Top -> this
        else -> Bottom
    }

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> when (opcode) {
            CmpOpcode.EQ -> ConstantDomainValue(false.toInt())
            CmpOpcode.NEQ -> ConstantDomainValue(true.toInt())
            else -> unreachable { log.error("$this does not support operation $opcode with $other") }
        }

        is NullDomainValue -> when (opcode) {
            CmpOpcode.EQ -> ConstantDomainValue(true.toInt())
            CmpOpcode.NEQ -> ConstantDomainValue(false.toInt())
            else -> unreachable { log.error("$this does not support operation $opcode with $other") }
        }

        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> UnsatDomainValue
        is NullDomainValue -> SatDomainValue
        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> SatDomainValue
        is NullDomainValue -> UnsatDomainValue
        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }
}

object NullableDomainValue : NullityAbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NullableDomainValue -> NullableDomainValue
        is Bottom -> NullableDomainValue
        else -> NullableDomainValue
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NullableDomainValue -> NullableDomainValue
        is Top -> this
        else -> Bottom
    }

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> Top
        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> Top
        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> Top
        is NullableDomainValue -> Top
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }
}

sealed interface SatisfiabilityAbstractDomainValue : AbstractDomainValue {

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue =
        unreachable { log.error("$this is unexpected satisfiability check") }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue =
        unreachable { log.error("$this is unexpected satisfiability check") }
}

object SatDomainValue : SatisfiabilityAbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is SatDomainValue -> SatDomainValue
        is Bottom -> SatDomainValue
        else -> Top
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is SatDomainValue -> SatDomainValue
        is Top -> SatDomainValue
        else -> Bottom
    }
}

object UnsatDomainValue : SatisfiabilityAbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is UnsatDomainValue -> UnsatDomainValue
        is Bottom -> UnsatDomainValue
        else -> Top
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is UnsatDomainValue -> UnsatDomainValue
        is Top -> UnsatDomainValue
        else -> Bottom
    }
}
