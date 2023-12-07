package org.vorpal.research.kex.state.transformer

import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.predicate
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.CharAtTerm
import org.vorpal.research.kex.state.term.CmpTerm
import org.vorpal.research.kex.state.term.ConcatTerm
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.ConstByteTerm
import org.vorpal.research.kex.state.term.ConstCharTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.ConstDoubleTerm
import org.vorpal.research.kex.state.term.ConstFloatTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.ConstLongTerm
import org.vorpal.research.kex.state.term.ConstShortTerm
import org.vorpal.research.kex.state.term.ConstStringTerm
import org.vorpal.research.kex.state.term.IndexOfTerm
import org.vorpal.research.kex.state.term.NegTerm
import org.vorpal.research.kex.state.term.StringContainsTerm
import org.vorpal.research.kex.state.term.StringLengthTerm
import org.vorpal.research.kex.state.term.StringParseTerm
import org.vorpal.research.kex.state.term.SubstringTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ToStringTerm
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kthelper.and
import org.vorpal.research.kthelper.compareTo
import org.vorpal.research.kthelper.div
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
import kotlin.math.abs

object ConstantPropagator : Transformer<ConstantPropagator>, IncrementalTransformer {
    private const val epsilon = 1e-5

    infix fun Double.eq(other: Double) = abs(this - other) < epsilon
    infix fun Double.neq(other: Double) = abs(this - other) >= epsilon
    infix fun Float.eq(other: Float) = abs(this - other) < epsilon
    infix fun Float.neq(other: Float) = abs(this - other) >= epsilon

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            apply(state.state),
            state.queries.map { query ->
                PredicateQuery(
                    apply(query.hardConstraints),
                    query.softConstraints.map { transform(it) }.toPersistentList()
                )
            }
        )
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val lhv = getConstantValue(term.lhv) ?: return term
        val rhv = getConstantValue(term.rhv) ?: return term
        return term {
            when (term.opcode) {
                BinaryOpcode.ADD -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv + nRhv)
                }
                BinaryOpcode.SUB -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv - nRhv)
                }
                BinaryOpcode.MUL -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv * nRhv)
                }
                BinaryOpcode.DIV -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv / nRhv)
                }
                BinaryOpcode.REM -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv % nRhv)
                }
                BinaryOpcode.SHL -> const(lhv.shl(rhv as Int))
                BinaryOpcode.SHR -> const(lhv.shr(rhv as Int))
                BinaryOpcode.USHR -> const(lhv.ushr(rhv as Int))
                BinaryOpcode.AND -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv and nRhv)
                }
                BinaryOpcode.OR -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv or nRhv)
                }
                BinaryOpcode.XOR -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv xor nRhv)
                }
            }
        }
    }

    override fun transformCharAtTerm(term: CharAtTerm): Term {
        val string = (term.string as? ConstStringTerm)?.value ?: return term
        val index = (term.index as? ConstIntTerm)?.value ?: return term
        return term { const(string[index]) }
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val lhv = getConstantValue(term.lhv) ?: return term
        val rhv = getConstantValue(term.rhv) ?: return term
        val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
        return term {
            when (term.opcode) {
                CmpOpcode.EQ -> when (nLhv) {
                    is Double -> const(nLhv eq nRhv.toDouble())
                    is Float -> const(nLhv eq nRhv.toFloat())
                    else -> const(nLhv == nRhv)
                }
                CmpOpcode.NEQ -> when (nLhv) {
                    is Double -> const(nLhv neq nRhv.toDouble())
                    is Float -> const(nLhv neq nRhv.toFloat())
                    else -> const(nLhv != nRhv)
                }
                CmpOpcode.LT -> const(nLhv < nRhv)
                CmpOpcode.GT -> const(nLhv > nRhv)
                CmpOpcode.LE -> const(nLhv <= nRhv)
                CmpOpcode.GE -> const(nLhv >= nRhv)
                CmpOpcode.CMP -> const(nLhv.compareTo(nRhv))
                CmpOpcode.CMPG -> const(nLhv.compareTo(nRhv))
                CmpOpcode.CMPL -> const(nLhv.compareTo(nRhv))
            }
        }
    }

    override fun transformConcatTerm(term: ConcatTerm): Term {
        val lhv = (term.lhv as? ConstStringTerm)?.value ?: return term
        val rhv = (term.rhv as? ConstStringTerm)?.value ?: return term
        return term { const(lhv + rhv) }
    }

    override fun transformIndexOf(term: IndexOfTerm): Term {
        val lhv = (term.string as? ConstStringTerm)?.value ?: return term
        val rhv = (term.substring as? ConstStringTerm)?.value ?: return term
        return term { const(lhv.indexOf(rhv)) }
    }

    override fun transformNegTerm(term: NegTerm): Term {
        val operand = getConstantValue(term.operand) ?: return term
        return term { const(-operand) }
    }

    override fun transformStringContainsTerm(term: StringContainsTerm): Term {
        val lhv = (term.string as? ConstStringTerm)?.value ?: return term
        val rhv = (term.substring as? ConstStringTerm)?.value ?: return term
        return term { const(rhv in lhv) }
    }

    override fun transformStringLengthTerm(term: StringLengthTerm): Term {
        val string = (term.string as? ConstStringTerm)?.value ?: return term
        return term { const(string.length) }
    }

    override fun transformStringParseTerm(term: StringParseTerm): Term {
        val string = (term.string as? ConstStringTerm)?.value ?: return term
        return when (term.type) {
            is KexBool -> term { const(string.toBoolean()) }
            is KexByte -> term { const(string.toByte()) }
            is KexChar -> term { const(string.first()) }
            is KexShort -> term { const(string.toShort()) }
            is KexInt -> term { const(string.toInt()) }
            is KexLong -> term { const(string.toLong()) }
            is KexFloat -> term { const(string.toFloat()) }
            is KexDouble -> term { const(string.toDouble()) }
            is KexNull -> term { const(null) }
            else -> term
        }
    }

    override fun transformSubstringTerm(term: SubstringTerm): Term {
        val lhv = (term.string as? ConstStringTerm)?.value ?: return term
        val offset = (term.offset as? ConstIntTerm)?.value ?: return term
        val length = (term.length as? ConstIntTerm)?.value ?: return term
        return term { const(lhv.substring(offset, length)) }
    }

    override fun transformToStringTerm(term: ToStringTerm): Term {
        return when (val value = term.value) {
            is ConstBoolTerm -> term { const(value.value.toString()) }
            is ConstByteTerm -> term { const(value.value.toString()) }
            is ConstCharTerm -> term { const(value.value.toString()) }
            is ConstClassTerm -> term { const(value.name) }
            is ConstDoubleTerm -> term { const(value.value.toString()) }
            is ConstFloatTerm -> term { const(value.value.toString()) }
            is ConstIntTerm -> term { const(value.value.toString()) }
            is ConstLongTerm -> term { const(value.value.toString()) }
            is ConstShortTerm -> term { const(null) }
            else -> term
        }
    }

    private fun toCompatibleTypes(lhv: Number, rhv: Number): Pair<Number, Number> = when (lhv) {
        is Long -> lhv to (rhv as Long)
        is Float -> lhv to (rhv as Float)
        is Double -> lhv to (rhv as Double)
        else -> lhv.toInt() to rhv.toInt()
    }

    private fun getConstantValue(term: Term): Number? = when (term) {
        is ConstBoolTerm -> term.value.toInt()
        is ConstByteTerm -> term.value
        is ConstCharTerm -> term.value.code.toShort()
        is ConstDoubleTerm -> term.value
        is ConstFloatTerm -> term.value
        is ConstIntTerm -> term.value
        is ConstLongTerm -> term.value
        is ConstShortTerm -> term.value
        else -> null
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val lhv = getConstantValue(predicate.lhv) ?: return predicate
        val rhv = getConstantValue(predicate.rhv) ?: return predicate
        val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
        val isNotError = when (nLhv) {
            is Float -> abs((nLhv - nRhv) as Float) < epsilon
            is Double -> abs((nLhv - nRhv) as Double) < epsilon
            else -> nLhv == nRhv
        }
        return when {
            isNotError -> predicate
            else -> predicate(predicate.type, predicate.location) {
                true equality false
            }
        }
    }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        val lhv = getConstantValue(predicate.lhv) ?: return predicate
        val rhv = getConstantValue(predicate.rhv) ?: return predicate
        val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
        val isNotError = when (nLhv) {
            is Float -> abs((nLhv - nRhv) as Float) >= epsilon
            is Double -> abs((nLhv - nRhv) as Double) >= epsilon
            else -> nLhv != nRhv
        }
        return when {
            isNotError -> predicate
            else -> predicate(predicate.type, predicate.location) {
                true inequality true
            }
        }
    }
}
