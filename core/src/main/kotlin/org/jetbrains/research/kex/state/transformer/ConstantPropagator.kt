package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.*
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
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
                is BinaryOpcode.Add -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv + nrhv)
                }
                is BinaryOpcode.Sub -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv - nrhv)
                }
                is BinaryOpcode.Mul -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv * nrhv)
                }
                is BinaryOpcode.Div -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv / nrhv)
                }
                is BinaryOpcode.Rem -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv % nrhv)
                }
                is BinaryOpcode.Shl -> const(lhv.shl(rhv as Int))
                is BinaryOpcode.Shr -> const(lhv.shr(rhv as Int))
                is BinaryOpcode.Ushr -> const(lhv.ushr(rhv as Int))
                is BinaryOpcode.And -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv and nrhv)
                }
                is BinaryOpcode.Or -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv or nrhv)
                }
                is BinaryOpcode.Xor -> {
                    val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
                    const(nlhv xor nrhv)
                }
            }
        }
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val lhv = getConstantValue(term.lhv) ?: return term
        val rhv = getConstantValue(term.rhv) ?: return term
        val (nlhv, nrhv) = toCompatibleTypes(lhv, rhv)
        return term {
            when (term.opcode) {
                is CmpOpcode.Eq -> when (nlhv) {
                    is Double -> const(nlhv eq nrhv.toDouble())
                    is Float -> const(nlhv eq nrhv.toFloat())
                    else -> const(nlhv == nrhv)
                }
                is CmpOpcode.Neq -> when (nlhv) {
                    is Double -> const(nlhv neq nrhv.toDouble())
                    is Float -> const(nlhv neq nrhv.toFloat())
                    else -> const(nlhv != nrhv)
                }
                is CmpOpcode.Lt -> const(nlhv < nrhv)
                is CmpOpcode.Gt -> const(nlhv > nrhv)
                is CmpOpcode.Le -> const(nlhv <= nrhv)
                is CmpOpcode.Ge -> const(nlhv >= nrhv)
                is CmpOpcode.Cmp -> const(nlhv.compareTo(nrhv))
                is CmpOpcode.Cmpg -> const(nlhv.compareTo(nrhv))
                is CmpOpcode.Cmpl -> const(nlhv.compareTo(nrhv))
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
    private fun mustBeEqual(right: Number, left: Number) =
            genMessage("must be equal to", right, left)
    private fun mustBeNotEqual(right: Number, left: Number) =
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
            else -> unreachable { mustBeEqual(nlhv, nrhv) }
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
            else -> unreachable { mustBeNotEqual(nlhv, nrhv) }
        }
    }
}
