package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.BinaryTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.type.BoolType
import org.jetbrains.research.kfg.type.IntType

object BoolTypeAdapter : Transformer<BoolTypeAdapter> {
    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val lhv = predicate.lhv
        val rhv = predicate.rhv
        val type = predicate.type
        val loc = predicate.location
        val res = when {
            lhv.type === BoolType && rhv.type === IntType -> pf.getEquality(lhv, tf.getCast(TF.boolType, rhv), type, loc)
            lhv.type === IntType && rhv.type === BoolType -> pf.getEquality(lhv, tf.getCast(TF.intType, rhv), type, loc)
            else -> predicate
        }
        return res
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val isBooleanOpcode = when (term.opcode) {
            BinaryOpcode.And() -> true
            BinaryOpcode.Or() -> true
            BinaryOpcode.Xor() -> true
            else -> false
        }
        val result =  when {
            isBooleanOpcode -> {
                val lhv = when {
                    term.lhv.type === BoolType -> tf.getCast(TF.intType, term.lhv)
                    term.lhv.type === IntType -> term.lhv
                    else -> unreachable { log.error("Non-boolean term in boolean binary: ${term.print()}") }
                }
                val rhv = when {
                    term.rhv.type === BoolType -> tf.getCast(TF.intType, term.rhv)
                    term.rhv.type === IntType -> term.rhv
                    else -> unreachable { log.error("Non-boolean term in boolean binary: ${term.print()}") }
                }
                tf.getBinary(term.opcode, lhv, rhv) as BinaryTerm
            }
            else -> term
        }
        return result
    }
}