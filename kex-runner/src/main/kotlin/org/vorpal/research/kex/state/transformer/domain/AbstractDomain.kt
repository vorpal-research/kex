package org.vorpal.research.kex.state.transformer.domain

import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.ConstByteTerm
import org.vorpal.research.kex.state.term.ConstCharTerm
import org.vorpal.research.kex.state.term.ConstDoubleTerm
import org.vorpal.research.kex.state.term.ConstFloatTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.ConstLongTerm
import org.vorpal.research.kex.state.term.ConstShortTerm
import org.vorpal.research.kex.state.term.NullTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.BoolType
import org.vorpal.research.kfg.type.ByteType
import org.vorpal.research.kfg.type.CharType
import org.vorpal.research.kfg.type.DoubleType
import org.vorpal.research.kfg.type.FloatType
import org.vorpal.research.kfg.type.IntType
import org.vorpal.research.kfg.type.LongType
import org.vorpal.research.kfg.type.Reference
import org.vorpal.research.kfg.type.ShortType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.commonSupertype
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


val AbstractDomainValue.isSat: Boolean
    get() = when (this) {
        is Top -> true
        is SatDomainValue -> true
        is UnsatDomainValue -> false
        is Bottom -> false
        else -> unreachable { log.error("Unexpected value for satisfiability domain value: $this") }
    }

val AbstractDomainValue.isUnsat: Boolean get() = !isSat

val AbstractDomainValue.isTrue: Boolean
    get() = this == DomainStorage.trueDomain

val AbstractDomainValue.isFalse: Boolean
    get() = this == DomainStorage.falseDomain

val AbstractDomainValue.isNull: Boolean
    get() = this is NullDomainValue

val AbstractDomainValue.canBeNull: Boolean
    get() = when (this) {
        is NullDomainValue -> true
        is NullableDomainValue -> true
        is Top -> true
        else -> false
    }

val AbstractDomainValue.isConstant: Boolean
    get() = this is IntervalDomainValue<*> && this.isConstant

class DomainStorage(value: AbstractDomainValue) {

    var value = value
        set(value) {
            field = field.assign(value)
        }

    companion object {
        val trueDomain: AbstractDomainValue = IntervalDomainValue(true.toInt())
        val falseDomain: AbstractDomainValue = IntervalDomainValue(false.toInt())

        fun toDomainValue(tf: TypeFactory, term: Term): AbstractDomainValue = when (term) {
            is ConstBoolTerm -> IntervalDomainValue(term.value.toInt())
            is ConstByteTerm -> IntervalDomainValue(term.value)
            is ConstCharTerm -> IntervalDomainValue(term.value.code)
            is ConstDoubleTerm -> IntervalDomainValue(term.value)
            is ConstFloatTerm -> IntervalDomainValue(term.value)
            is ConstIntTerm -> IntervalDomainValue(term.value)
            is ConstLongTerm -> IntervalDomainValue(term.value)
            is ConstShortTerm -> IntervalDomainValue(term.value)
            is NullTerm -> NullDomainValue
            else -> when (term.type) {
                is KexArray -> ArrayDomainValue(
                    Top,
                    TypeDomainValue(term.type.getKfgType(tf)),
                    ArrayDomainValue.TOP_LENGTH,
                )

                is KexPointer -> PtrDomainValue(
                    Top,
                    TypeDomainValue(term.type.getKfgType(tf)),
                )

                else -> Top
            }
        }

        fun newArray(
            tf: TypeFactory,
            term: Term,
            length: AbstractDomainValue = ArrayDomainValue.TOP_LENGTH
        ): AbstractDomainValue = ArrayDomainValue(
            NonNullableDomainValue,
            TypeDomainValue(term.type.getKfgType(tf)),
            length
        )

        fun newPtr(tf: TypeFactory, term: Term): AbstractDomainValue = PtrDomainValue(
            NonNullableDomainValue,
            TypeDomainValue(term.type.getKfgType(tf))
        )
    }
}

sealed interface AbstractDomainValue {
    fun join(other: AbstractDomainValue): AbstractDomainValue
    fun meet(other: AbstractDomainValue): AbstractDomainValue

    fun assign(other: AbstractDomainValue): AbstractDomainValue

    fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun apply(opcode: UnaryOpcode): AbstractDomainValue {
        return unreachable { log.error("$this does not support operation $opcode") }
    }

    fun cast(type: Type): AbstractDomainValue {
        return unreachable { log.error("$this cannot be cast to $type") }
    }

    fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this cannot be checked for equality with $other") }
    }

    fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue {
        return unreachable { log.error("$this cannot be checked for inequality with $other") }
    }

    fun satisfiesType(type: Type): AbstractDomainValue {
        return unreachable { log.error("$this cannot be checked for satisfiability of type $type") }
    }
}

data object Top : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun meet(other: AbstractDomainValue): AbstractDomainValue = other

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other

    override fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue = Top

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = Top

    override fun apply(opcode: UnaryOpcode): AbstractDomainValue = when (opcode) {
        UnaryOpcode.LENGTH -> IntervalDomainValue(0, Int.MAX_VALUE)
        UnaryOpcode.NEG -> Top
    }

    override fun cast(type: Type): AbstractDomainValue = Top

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesType(type: Type): AbstractDomainValue = Top
}

data object Bottom : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = other
    override fun meet(other: AbstractDomainValue): AbstractDomainValue = Bottom

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = Top
    override fun satisfiesType(type: Type): AbstractDomainValue = Top
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

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other

    @Suppress("UNCHECKED_CAST")
    private fun <T : Number> lowerBound(num: T): T = when (num) {
        is Byte -> Byte.MIN_VALUE
        is Short -> Short.MIN_VALUE
        is Int -> Int.MIN_VALUE
        is Long -> Long.MIN_VALUE
        is Float -> Float.NEGATIVE_INFINITY
        is Double -> Double.NEGATIVE_INFINITY
        else -> unreachable { log.error("Unknown number impl $num") }
    } as T

    @Suppress("UNCHECKED_CAST")
    private fun <T : Number> upperBound(num: T) = when (num) {
        is Byte -> Byte.MAX_VALUE
        is Short -> Short.MAX_VALUE
        is Int -> Int.MAX_VALUE
        is Long -> Long.MAX_VALUE
        is Float -> Float.POSITIVE_INFINITY
        is Double -> Double.POSITIVE_INFINITY
        else -> unreachable { log.error("Unknown number impl $num") }
    } as T

    override fun apply(opcode: BinaryOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top

        is IntervalDomainValue<*> -> when (opcode) {
            BinaryOpcode.ADD -> {
                val lb = min + other.min
                val ub = max + other.max
                when {
                    lb < min || lb < other.min -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    ub < max || ub < other.max -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    else -> IntervalDomainValue(lb, ub)
                }
            }

            BinaryOpcode.SUB -> {
                val lb = min - other.max
                val ub = max - other.min
                when {
                    lb > min -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    ub < max -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    else -> IntervalDomainValue(lb, ub)
                }
            }

            BinaryOpcode.MUL -> {
                val lb = minOf(min * other.min, max * other.min, min * other.max, max * other.max)
                val ub = maxOf(min * other.min, max * other.min, min * other.max, max * other.max)
                when {
                    lb > min -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    ub < max -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    else -> IntervalDomainValue(lb, ub)
                }
            }

            BinaryOpcode.DIV -> {
                val lb = minOf(min / other.min, max / other.min, min / other.max, max / other.max)
                val ub = maxOf(min / other.min, max / other.min, min / other.max, max / other.max)
                when {
                    lb > min -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    ub < max -> IntervalDomainValue(lowerBound(min), upperBound(max))
                    else -> IntervalDomainValue(lb, ub)
                }
            }

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
                else -> DomainStorage.falseDomain
            }

            CmpOpcode.NEQ -> Top
            CmpOpcode.LT -> when {
                other.max < min -> DomainStorage.falseDomain
                other.min > max -> DomainStorage.trueDomain
                else -> Top
            }

            CmpOpcode.GT -> when {
                other.max < min -> DomainStorage.trueDomain
                other.min > max -> DomainStorage.falseDomain
                else -> Top
            }

            CmpOpcode.LE -> when {
                other.max < min -> DomainStorage.falseDomain
                other.min > max -> DomainStorage.trueDomain
                this.isConstant && other.isConstant && min == other.min -> DomainStorage.trueDomain
                else -> Top
            }

            CmpOpcode.GE -> when {
                other.max < min -> DomainStorage.trueDomain
                other.min > max -> DomainStorage.falseDomain
                this.isConstant && other.isConstant && min == other.min -> DomainStorage.trueDomain
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

    override fun cast(type: Type): AbstractDomainValue = when (type) {
        is BoolType -> when {
            0 < min || 0 > max -> DomainStorage.trueDomain
            else -> Top
        }

        is ByteType -> IntervalDomainValue(min.toByte(), max.toByte())
        is CharType -> IntervalDomainValue(min.toInt().toChar().code, max.toInt().toChar().code)
        is ShortType -> IntervalDomainValue(min.toShort(), max.toShort())
        is IntType -> IntervalDomainValue(min.toInt(), max.toInt())
        is LongType -> IntervalDomainValue(min.toLong(), max.toLong())
        is FloatType -> IntervalDomainValue(min.toFloat(), max.toFloat())
        is DoubleType -> IntervalDomainValue(min.toDouble(), max.toDouble())
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
    override fun cast(type: Type): AbstractDomainValue = when (type) {
        is ArrayType -> ArrayDomainValue(this, TypeDomainValue(type), ArrayDomainValue.TOP_LENGTH)
        is Reference -> PtrDomainValue(this, TypeDomainValue(type))
        else -> unreachable { log.error("Cannot cast $this to type $type") }
    }
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

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> when (opcode) {
            CmpOpcode.EQ -> DomainStorage.falseDomain
            CmpOpcode.NEQ -> DomainStorage.trueDomain
            else -> unreachable { log.error("$this does not support operation $opcode with $other") }
        }

        is NullableDomainValue -> Top
        is TermDomainValue -> apply(opcode, other.nullity)
        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> UnsatDomainValue
        is NullableDomainValue -> Top
        is TermDomainValue -> satisfiesEquality(other.nullity)
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> SatDomainValue
        is NullableDomainValue -> Top
        is TermDomainValue -> satisfiesInequality(other.nullity)
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

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> when (opcode) {
            CmpOpcode.EQ -> DomainStorage.falseDomain
            CmpOpcode.NEQ -> DomainStorage.trueDomain
            else -> unreachable { log.error("$this does not support operation $opcode with $other") }
        }

        is NullDomainValue -> when (opcode) {
            CmpOpcode.EQ -> DomainStorage.trueDomain
            CmpOpcode.NEQ -> DomainStorage.falseDomain
            else -> unreachable { log.error("$this does not support operation $opcode with $other") }
        }

        is NullableDomainValue -> Top
        is TermDomainValue -> apply(opcode, other.nullity)
        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> UnsatDomainValue
        is NullDomainValue -> SatDomainValue
        is NullableDomainValue -> Top
        is TermDomainValue -> satisfiesEquality(other.nullity)
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> SatDomainValue
        is NullDomainValue -> UnsatDomainValue
        is NullableDomainValue -> Top
        is TermDomainValue -> satisfiesInequality(other.nullity)
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }

    override fun satisfiesType(type: Type): AbstractDomainValue = DomainStorage.falseDomain
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

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> Top
        is NullableDomainValue -> Top
        is TermDomainValue -> apply(opcode, other.nullity)
        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> Top
        is NullableDomainValue -> Top
        is TermDomainValue -> satisfiesEquality(other.nullity)
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NonNullableDomainValue -> Top
        is NullDomainValue -> Top
        is NullableDomainValue -> Top
        is TermDomainValue -> satisfiesInequality(other.nullity)
        else -> unreachable { log.error("$this != $other is unexpected satisfiability check") }
    }
}

data class TypeDomainValue(val type: Type) : AbstractDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is Bottom -> this
        is TypeDomainValue -> when {
            other.type.isSubtypeOfCached(type) -> this
            this.type.isSubtypeOfCached(other.type) -> other
            else -> TypeDomainValue(commonSupertype(setOf(type, other.type))!!)
        }

        else -> unreachable { log.error("Attempting to join $this and $other") }
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> this
        is Bottom -> Bottom
        is TypeDomainValue -> when {
            other.type.isSubtypeOfCached(type) -> other
            this.type.isSubtypeOfCached(other.type) -> this
            else -> unreachable { log.error("Unexpected meet on $this and $other") }
        }

        else -> unreachable { log.error("Attempting to meet $this and $other") }
    }

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is TypeDomainValue -> if (other.type.isSubtypeOfCached(type)) other else this
        else -> other
    }

    override fun cast(type: Type): AbstractDomainValue = when {
        type.isSubtypeOfCached(this.type) -> TypeDomainValue(type)
        else -> this
    }

    override fun satisfiesType(type: Type): AbstractDomainValue = when {
        type.isSubtypeOfCached(this.type) -> DomainStorage.trueDomain
        this.type.isSubtypeOfCached(type) -> DomainStorage.trueDomain
        else -> DomainStorage.falseDomain
    }
}

sealed interface TermDomainValue : AbstractDomainValue {
    val nullity: AbstractDomainValue
    val type: AbstractDomainValue

    override fun apply(opcode: CmpOpcode, other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NullityAbstractDomainValue -> nullity.apply(opcode, other)
        is TermDomainValue -> nullity.apply(opcode, other.nullity)
        else -> unreachable { log.error("$this does not support operation $opcode with $other") }
    }

    override fun satisfiesEquality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NullityAbstractDomainValue -> nullity.satisfiesEquality(other)
        is TermDomainValue -> nullity.satisfiesEquality(other.nullity)
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesInequality(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is NullityAbstractDomainValue -> nullity.satisfiesInequality(other)
        is TermDomainValue -> nullity.satisfiesInequality(other.nullity)
        else -> unreachable { log.error("$this == $other is unexpected satisfiability check") }
    }

    override fun satisfiesType(type: Type): AbstractDomainValue = when {
        nullity.isNull -> DomainStorage.falseDomain
        nullity.canBeNull -> Top
        else -> this.type.satisfiesType(type)
    }
}

data class PtrDomainValue(
    override val nullity: AbstractDomainValue,
    override val type: AbstractDomainValue
) : TermDomainValue {
    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is Bottom -> other
        is NullityAbstractDomainValue -> PtrDomainValue(nullity.join(other), type)
        is PtrDomainValue -> PtrDomainValue(nullity.join(other.nullity), type.join(other.type))
        is ArrayDomainValue -> PtrDomainValue(nullity.join(other.nullity), type.join(other.type))
        else -> unreachable { log.error("Attempting to join $this and $other") }
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> this
        is Bottom -> Bottom
        is NullityAbstractDomainValue -> PtrDomainValue(nullity.meet(other), type)
        is PtrDomainValue -> PtrDomainValue(nullity.meet(other.nullity), type.meet(other.type))
        else -> unreachable { log.error("Attempting to join $this and $other") }
    }

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NullityAbstractDomainValue -> PtrDomainValue(nullity.assign(other), type)
        is TypeDomainValue -> when {
            other.type is ArrayType -> ArrayDomainValue(nullity, type.assign(other), ArrayDomainValue.TOP_LENGTH)
            else -> PtrDomainValue(nullity, type.assign(other))
        }

        else -> other
    }

    override fun cast(type: Type): AbstractDomainValue = when (type) {
        is ArrayType -> ArrayDomainValue(nullity, this.type.cast(type), ArrayDomainValue.TOP_LENGTH)
        else -> PtrDomainValue(nullity, this.type.cast(type))
    }
}

data class ArrayDomainValue(
    override val nullity: AbstractDomainValue,
    override val type: AbstractDomainValue,
    val length: AbstractDomainValue
) : TermDomainValue {
    companion object {
        val TOP_LENGTH = IntervalDomainValue(0, Int.MAX_VALUE)
    }

    override fun join(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> Top
        is Bottom -> this
        is NullityAbstractDomainValue -> ArrayDomainValue(nullity.join(other), type, length)
        is PtrDomainValue -> PtrDomainValue(
            nullity.join(other.nullity),
            type.join(other.type)
        )

        is ArrayDomainValue -> ArrayDomainValue(
            nullity.join(other.nullity),
            type.join(other.type),
            length.join(other.length)
        )

        else -> unreachable { log.error("$this join $other is unexpected operation") }
    }

    override fun meet(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is Top -> this
        is Bottom -> Bottom
        is NullityAbstractDomainValue -> ArrayDomainValue(nullity.meet(other), type, length)
        is ArrayDomainValue -> ArrayDomainValue(
            nullity.meet(other.nullity),
            type.join(other.type),
            length.meet(other.length)
        )

        else -> unreachable { log.error("$this meet $other is unexpected operation") }
    }

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = when (other) {
        is NullityAbstractDomainValue -> ArrayDomainValue(nullity.assign(other), type, length)
        is PtrDomainValue -> ArrayDomainValue(nullity.assign(other.nullity), type.assign(other.type), length)
        is ArrayDomainValue -> ArrayDomainValue(
            nullity.assign(other.nullity),
            type.assign(other.type),
            length.assign(other.length)
        )

        is TypeDomainValue -> ArrayDomainValue(nullity, type.assign(other), length)
        else -> other
    }

    override fun apply(opcode: UnaryOpcode): AbstractDomainValue = when (opcode) {
        UnaryOpcode.LENGTH -> length
        UnaryOpcode.NEG -> unreachable { log.error("$this does not support operation $opcode") }
    }

    override fun cast(type: Type): AbstractDomainValue = ArrayDomainValue(nullity, this.type.cast(type), length)
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

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other
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

    override fun assign(other: AbstractDomainValue): AbstractDomainValue = other
}
