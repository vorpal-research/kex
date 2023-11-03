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
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kthelper.and
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.compareTo
import org.vorpal.research.kthelper.div
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.maxOf
import org.vorpal.research.kthelper.minOf
import org.vorpal.research.kthelper.minus
import org.vorpal.research.kthelper.or
import org.vorpal.research.kthelper.plus
import org.vorpal.research.kthelper.rem
import org.vorpal.research.kthelper.shl
import org.vorpal.research.kthelper.shr
import org.vorpal.research.kthelper.times
import org.vorpal.research.kthelper.toInt
import org.vorpal.research.kthelper.unaryMinus
import org.vorpal.research.kthelper.ushr
import org.vorpal.research.kthelper.xor


class DomainStorage(var value: AbstractDomainValue)

sealed interface AbstractDomainValue {
    fun join(other: AbstractDomainValue): AbstractDomainValue
    fun meet(other: AbstractDomainValue): AbstractDomainValue

    fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun apply(opcode: UnaryOpcode): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun cast(type: KexType): AbstractDomainValue {
        return unreachable { log.error("$this cannot be cast to $type") }
    }

    fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue
    fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue

    fun deepCopy(mappings: MutableMap<DomainStorage, DomainStorage>): AbstractDomainValue = this
}

data object Top : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun meet(other: AbstractDomainValue): AbstractDomainValue = other

    override fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue = Top

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = Top

    override fun apply(opcode: UnaryOpcode): AbstractDomainValue = when (opcode) {
        UnaryOpcode.LENGTH -> IntervalDomainValue(0, Int.MAX_VALUE)
        UnaryOpcode.NEG -> Top
    }

    override fun cast(type: KexType): AbstractDomainValue = Top

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = Top
}

data object Bottom : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = other
    override fun meet(other: AbstractDomainValue): AbstractDomainValue = Bottom

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = Top
}

sealed interface NumberAbstractDomainValue : AbstractDomainValue

data class IntervalDomainValue<T : Number>(val min: T, val max: T) : NumberAbstractDomainValue {
    val isConstant: Boolean get() = min == max

    constructor(value: T) : this(value, value)

    operator fun contains(other: AbstractDomainValue): Boolean = when (other) {
        is IntervalDomainValue<*> -> min <= other.min && other.max <= max
        else -> false
    }

    private fun intersect(other: AbstractDomainValue): Boolean = when (other) {
        is IntervalDomainValue<*> -> maxOf(min, other.min) <= minOf(max, other.max)
        else -> false
    }

    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is IntervalDomainValue<*> -> IntervalDomainValue(
            minOf(min, other.min),
            maxOf(max, other.max)
        )

        is Bottom -> this
        else -> Top
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is IntervalDomainValue<*> -> IntervalDomainValue(
            maxOf(min, other.min),
            minOf(max, other.max)
        )

        is Top -> this
        else -> Bottom
    }

    override fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top

        is IntervalDomainValue<*> -> when (opcode) {
            BinaryOpcode.ADD -> IntervalDomainValue(other.min + other.min, max + other.max)
            BinaryOpcode.SUB -> IntervalDomainValue(min - other.max, max + other.min)
            BinaryOpcode.MUL -> IntervalDomainValue(
                minOf(min * other.min, max * other.min, min * other.max, max * other.max),
                maxOf(min * other.min, max * other.min, min * other.max, max * other.max)
            )

            BinaryOpcode.DIV -> IntervalDomainValue(
                minOf(min / other.min, max / other.min, min / other.max, max / other.max),
                maxOf(min / other.min, max / other.min, min / other.max, max / other.max)
            )

            BinaryOpcode.REM -> when {
                this.isConstant && other.isConstant -> IntervalDomainValue(min % other.min)
                else -> Top
            }

            BinaryOpcode.SHL -> when {
                this.isConstant && other.isConstant -> IntervalDomainValue(min.shl(other.min.toInt()))
                else -> Top
            }

            BinaryOpcode.SHR -> when {
                this.isConstant && other.isConstant -> IntervalDomainValue(min.shr(other.min.toInt()))
                else -> Top
            }

            BinaryOpcode.USHR -> when {
                this.isConstant && other.isConstant -> IntervalDomainValue(min.ushr(other.min.toInt()))
                else -> Top
            }

            BinaryOpcode.AND -> when {
                this.isConstant && other.isConstant -> IntervalDomainValue(min and other.min)
                else -> Top
            }

            BinaryOpcode.OR -> when {
                this.isConstant && other.isConstant -> IntervalDomainValue(min or other.min)
                else -> Top
            }

            BinaryOpcode.XOR -> when {
                this.isConstant && other.isConstant -> IntervalDomainValue(min xor other.min)
                else -> Top
            }
        }

        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top

        is IntervalDomainValue<*> -> when (opcode) {
            CmpOpcode.EQ -> when {
                this.intersect(other) -> Top
                else -> IntervalDomainValue(false.toInt())
            }

            CmpOpcode.NEQ -> Top
            CmpOpcode.LT -> when {
                other.max < min -> IntervalDomainValue(false.toInt())
                other.min > max -> IntervalDomainValue(true.toInt())
                else -> Top
            }

            CmpOpcode.GT -> when {
                other.max < min -> IntervalDomainValue(true.toInt())
                other.min > max -> IntervalDomainValue(false.toInt())
                else -> Top
            }

            CmpOpcode.LE -> when {
                other.max < min -> IntervalDomainValue(false.toInt())
                other.min > max -> IntervalDomainValue(true.toInt())
                this.isConstant && other.isConstant && min == other.min -> IntervalDomainValue(true.toInt())
                else -> Top
            }

            CmpOpcode.GE -> when {
                other.max < min -> IntervalDomainValue(true.toInt())
                other.min > max -> IntervalDomainValue(false.toInt())
                this.isConstant && other.isConstant && min == other.min -> IntervalDomainValue(true.toInt())
                else -> Top
            }

            CmpOpcode.CMP -> when {
                other.max < min -> IntervalDomainValue(1)
                other.min > max -> IntervalDomainValue(-1)
                else -> Top
            }

            CmpOpcode.CMPG -> when {
                other.max < min -> IntervalDomainValue(1)
                other.min > max -> IntervalDomainValue(-1)
                else -> Top
            }

            CmpOpcode.CMPL -> when {
                other.max < min -> IntervalDomainValue(1)
                other.min > max -> IntervalDomainValue(-1)
                else -> Top
            }

            else -> Top
        }

        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun apply(opcode: UnaryOpcode): AbstractDomainValue = when (opcode) {
        UnaryOpcode.NEG -> IntervalDomainValue(-max, -min)
        UnaryOpcode.LENGTH -> unreachable { log.error("$this does not support operation $opcode") }
    }

    override fun cast(type: KexType): AbstractDomainValue = when (type) {
        is KexBool -> when {
            0 < min || 0 > max -> IntervalDomainValue(true.toInt())
            else -> Top
        }

        is KexByte -> IntervalDomainValue(min.toByte(), max.toByte())
        is KexChar -> IntervalDomainValue(min.toInt().toChar().code, max.toInt().toChar().code)
        is KexShort -> IntervalDomainValue(min.toShort(), max.toShort())
        is KexInt -> IntervalDomainValue(min.toInt(), max.toInt())
        is KexLong -> IntervalDomainValue(min.toLong(), max.toLong())
        is KexFloat -> IntervalDomainValue(min.toFloat(), max.toFloat())
        is KexDouble -> IntervalDomainValue(min.toDouble(), max.toDouble())
        else -> unreachable { log.error("$this cannot be cast to $type") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top

        is IntervalDomainValue<*> -> when {
            this.intersect(other) -> SatDomainValue
            else -> UnsatDomainValue
        }

        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is IntervalDomainValue<*> -> SatDomainValue
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }

}

sealed interface NullityAbstractDomainValue : AbstractDomainValue {
    override fun cast(type: KexType): AbstractDomainValue = this
}

data object NonNullableDomainValue : NullityAbstractDomainValue {
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
            CmpOpcode.EQ -> IntervalDomainValue(false.toInt())
            CmpOpcode.NEQ -> IntervalDomainValue(true.toInt())
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

data object NullDomainValue : NullityAbstractDomainValue {
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
            CmpOpcode.EQ -> IntervalDomainValue(false.toInt())
            CmpOpcode.NEQ -> IntervalDomainValue(true.toInt())
            else -> unreachable { log.error("$this does not support operation $opcode with $other") }
        }

        is NullDomainValue -> when (opcode) {
            CmpOpcode.EQ -> IntervalDomainValue(true.toInt())
            CmpOpcode.NEQ -> IntervalDomainValue(false.toInt())
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

data object NullableDomainValue : NullityAbstractDomainValue {
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

data class ArrayDomainValue(val array: AbstractDomainValue, val length: AbstractDomainValue) : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is Bottom -> this
        is ArrayDomainValue -> ArrayDomainValue(array.join(other.array), length.join(other.length))
        else -> unreachable { log.error("$this join $other is unexpected operation") }
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> this
        is Bottom -> Bottom
        is ArrayDomainValue -> ArrayDomainValue(array.meet(other.array), length.meet(other.length))
        else -> unreachable { log.error("$this meet $other is unexpected operation") }
    }

    override fun apply(opcode: UnaryOpcode): AbstractDomainValue = when (opcode) {
        UnaryOpcode.LENGTH -> length
        UnaryOpcode.NEG -> unreachable { log.error("$this does not support operation $opcode") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NullityAbstractDomainValue -> array.satisfiesEquality(other)
        is ArrayDomainValue -> array.satisfiesEquality(other.array)
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NullityAbstractDomainValue -> array.satisfiesInequality(other)
        is ArrayDomainValue -> array.satisfiesInequality(other.array)
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }
}

sealed interface SatisfiabilityAbstractDomainValue : AbstractDomainValue {

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue =
        unreachable { log.error("$this is unexpected satisfiability check") }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue =
        unreachable { log.error("$this is unexpected satisfiability check") }
}

data object SatDomainValue : SatisfiabilityAbstractDomainValue {
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

data object UnsatDomainValue : SatisfiabilityAbstractDomainValue {
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
