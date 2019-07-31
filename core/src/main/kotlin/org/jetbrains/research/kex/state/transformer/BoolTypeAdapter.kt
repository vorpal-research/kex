package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.ktype.mergeTypes
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.predicate
import org.jetbrains.research.kex.state.term.BinaryTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.type.TypeFactory

class BoolTypeAdapter(val types: TypeFactory) : Transformer<BoolTypeAdapter> {
    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val lhv = predicate.lhv
        val rhv = predicate.rhv
        val type = predicate.type
        val loc = predicate.location
        return when {
            lhv.type is KexBool && rhv.type is KexInt -> predicate(type, loc) { lhv equality (rhv `as` KexBool()) }
            lhv.type is KexInt && rhv.type is KexBool -> predicate(type, loc) { lhv equality (rhv `as` KexInt()) }
            else -> predicate
        }
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val isBooleanOpcode = when (term.opcode) {
            BinaryOpcode.And() -> true
            BinaryOpcode.Or() -> true
            BinaryOpcode.Xor() -> true
            else -> false
        }
        return when {
            term.lhv.type == term.rhv.type -> term
            isBooleanOpcode -> {
                val lhv = when {
                    term.lhv.type is KexBool -> term { term.lhv `as` KexInt() }
                    term.lhv.type is KexInt -> term.lhv
                    else -> unreachable { log.error("Non-boolean term in boolean binary: $term") }
                }
                val rhv = when {
                    term.rhv.type is KexBool -> term { term.rhv `as` KexInt() }
                    term.rhv.type is KexInt -> term.rhv
                    else -> unreachable { log.error("Non-boolean term in boolean binary: $term") }
                }
                val newType = mergeTypes(types, lhv.type, rhv.type)
                term { lhv.apply(newType, term.opcode, rhv) }
            }
            else -> term
        }
    }
}