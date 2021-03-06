package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kthelper.*
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import kotlin.math.abs

object ConstantPropagator : Transformer<ConstantPropagator> {
    private const val epsilon = 1e-5

    infix fun Double.eq(other: Double) = (this - other) < epsilon
    infix fun Double.neq(other: Double) = (this - other) >= epsilon
    infix fun Float.eq(other: Float) = (this - other) < epsilon
    infix fun Float.neq(other: Float) = (this - other) >= epsilon

    override fun apply(ps: PredicateState): PredicateState {
        return ps
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

    override fun transformNegTerm(term: NegTerm): Term {
        val operand = getConstantValue(term.operand) ?: return term
        return term { const(operand) }
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

    private fun genMessage(word: String, right: Number, left: Number) =
            log.error("Obvious error detected: $right $word $left")
    private fun mustBeEqual(right: Number, left: Number) =
            genMessage("must be equal to", right, left)
    private fun mustBeNotEqual(right: Number, left: Number) =
            genMessage("should not be equal to", right, left)

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
            else -> unreachable { mustBeEqual(nLhv, nRhv) }
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
            else -> unreachable { mustBeNotEqual(nLhv, nRhv) }
        }
    }
}
