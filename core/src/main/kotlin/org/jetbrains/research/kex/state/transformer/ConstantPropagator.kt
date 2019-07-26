package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import kotlin.math.abs

object ConstantPropagator : Transformer<ConstantPropagator> {
    private const val epsilon = 1e-5

    infix fun Double.eq(other: Double) = (this - other) < epsilon
    infix fun Double.neq(other: Double) = (this - other) >= epsilon
    infix fun Float.eq(other: Float) = (this - other) < epsilon
    infix fun Float.neq(other: Float) = (this - other) >= epsilon

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val lhv = getConstantValue(term.lhv) ?: return term
        val rhv = getConstantValue(term.rhv) ?: return term
        return when (term.opcode) {
            is BinaryOpcode.Add -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv + nrhv)
            }
            is BinaryOpcode.Sub -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv - nrhv)
            }
            is BinaryOpcode.Mul -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv * nrhv)
            }
            is BinaryOpcode.Div -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv / nrhv)
            }
            is BinaryOpcode.Rem -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv % nrhv)
            }
            is BinaryOpcode.Shl -> tf.getConstant(lhv.shl(rhv as Int))
            is BinaryOpcode.Shr -> tf.getConstant(lhv.shr(rhv as Int))
            is BinaryOpcode.Ushr -> tf.getConstant(lhv.ushr(rhv as Int))
            is BinaryOpcode.And -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv and nrhv)
            }
            is BinaryOpcode.Or -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv or nrhv)
            }
            is BinaryOpcode.Xor -> {
                val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                tf.getConstant(nlhv xor nrhv)
            }
        }
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val lhv = getConstantValue(term.lhv) ?: return term
        val rhv = getConstantValue(term.rhv) ?: return term
        val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
        return when (term.opcode) {
            is CmpOpcode.Eq -> when (nlhv) {
                is Double -> tf.getBool(nlhv eq nrhv.toDouble())
                is Float -> tf.getBool(nlhv eq nrhv.toFloat())
                else -> tf.getBool(nlhv == nrhv)
            }
            is CmpOpcode.Neq -> when (nlhv) {
                is Double -> tf.getBool(nlhv neq nrhv.toDouble())
                is Float -> tf.getBool(nlhv neq nrhv.toFloat())
                else -> tf.getBool(nlhv != nrhv)
            }
            is CmpOpcode.Lt -> tf.getBool(nlhv < nrhv)
            is CmpOpcode.Gt -> tf.getBool(nlhv > nrhv)
            is CmpOpcode.Le -> tf.getBool(nlhv <= nrhv)
            is CmpOpcode.Ge -> tf.getBool(nlhv >= nrhv)
            is CmpOpcode.Cmp -> tf.getInt(nlhv.compareTo(nrhv))
            is CmpOpcode.Cmpg -> tf.getInt(nlhv.compareTo(nrhv))
            is CmpOpcode.Cmpl -> tf.getInt(nlhv.compareTo(nrhv))
        }
    }

    override fun transformNegTerm(term: NegTerm): Term {
        val operand = getConstantValue(term.operand) ?: return term
        return tf.getConstant(operand)
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
        is ConstCharTerm -> term.value.toShort()
        is ConstDoubleTerm -> term.value
        is ConstFloatTerm -> term.value
        is ConstIntTerm -> term.value
        is ConstLongTerm -> term.value
        is ConstShortTerm -> term.value
        else -> null
    }

    private fun genMessage(word: String, right: Number, left: Number) =
            log.error("Obvious error detected: $right $word $left")
    private fun mustEqual(right: Number, left: Number) =
            genMessage("must be equal to", right, left)
    private fun mustNotEqual(right: Number, left: Number) =
            genMessage("should not be equal to", right, left)

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val lhv = getConstantValue(predicate.lhv) ?: return predicate
        val rhv = getConstantValue(predicate.rhv) ?: return predicate
        val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
        val isNotError = when (nlhv) {
            is Float -> abs((nlhv - nrhv) as Float) < epsilon
            is Double -> abs((nlhv - nrhv) as Double) < epsilon
            else -> nlhv == nrhv
        }
        return when {
            isNotError -> predicate
            else -> unreachable { mustEqual(nlhv, nrhv) }
        }
    }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        val lhv = getConstantValue(predicate.lhv) ?: return predicate
        val rhv = getConstantValue(predicate.rhv) ?: return predicate
        val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
        val isNotError = when (nlhv) {
            is Float -> abs((nlhv - nrhv) as Float) >= epsilon
            is Double -> abs((nlhv - nrhv) as Double) >= epsilon
            else -> nlhv != nrhv
        }
        return when {
            isNotError -> predicate
            else -> unreachable { mustNotEqual(nlhv, nrhv) }
        }
    }
}
