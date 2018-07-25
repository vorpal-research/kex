package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode

class ConstantPropagator : Transformer<ConstantPropagator> {
    private val epsilon = 0.00001

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
            is BinaryOpcode.Shl -> {
                val bits = rhv as? Int ?: unreachable { log.error("Non-integer bit count in shift operation") }
                tf.getConstant(lhv.shl(bits))
            }
            is BinaryOpcode.Shr -> {
                val bits = rhv as? Int ?: unreachable { log.error("Non-integer bit count in shift operation") }
                tf.getConstant(lhv.shr(bits))
            }
            is BinaryOpcode.Ushr -> {
                val bits = rhv as? Int ?: unreachable { log.error("Non-integer bit count in shift operation") }
                tf.getConstant(lhv.ushr(bits))
            }
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
                is Double -> tf.getBool((nlhv - nrhv.toDouble()) < epsilon)
                is Float -> tf.getBool((nlhv - nrhv.toFloat()) < epsilon)
                else -> tf.getBool(nlhv == nrhv)
            }
            is CmpOpcode.Neq -> when (nlhv) {
                is Double -> tf.getBool((nlhv - nrhv.toDouble()) >= epsilon)
                is Float -> tf.getBool((nlhv - nrhv.toFloat()) >= epsilon)
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
        is Long -> {
            val rhvl = rhv as? Long ?: unreachable { log.error("Non-compatible types in binary term: $lhv and $rhv") }
            lhv to rhvl
        }
        is Float -> {
            val rhvf = rhv as? Float ?: unreachable { log.error("Non-compatible types in binary term: $lhv and $rhv") }
            lhv to rhvf
        }
        is Double -> {
            val rhvd = rhv as? Double ?: unreachable { log.error("Non-compatible types in binary term: $lhv and $rhv") }
            lhv to rhvd
        }
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
}
