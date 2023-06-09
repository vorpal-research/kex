package org.vorpal.research.kex.state.transformer

import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexInteger
import org.vorpal.research.kex.ktype.mergeTypes
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.predicate
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.CmpTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

class BoolTypeAdapter(val types: TypeFactory) : Transformer<BoolTypeAdapter>, IncrementalTransformer {

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

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val lhv = predicate.lhv
        val rhv = predicate.rhv
        val type = predicate.type
        val loc = predicate.location
        return when {
            lhv.type is KexBool && rhv.type is KexInt -> predicate(type, loc) { lhv equality (rhv `as` KexBool) }
            lhv.type is KexInt && rhv.type is KexBool -> predicate(type, loc) { lhv equality (rhv `as` KexInt) }
            else -> predicate
        }
    }

    private fun adaptTerm(term: Term) = when (term.type) {
        is KexBool -> term { term `as` KexInt }
        is KexInt -> term
        is KexInteger -> term { term `as` KexInt }
        else -> unreachable { log.error("Non-boolean term in boolean binary: $term") }
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val isBooleanOpcode = when (term.opcode) {
            BinaryOpcode.AND -> true
            BinaryOpcode.OR -> true
            BinaryOpcode.XOR -> true
            else -> false
        }
        return when {
            term.lhv.type == term.rhv.type -> term
            isBooleanOpcode -> {
                val lhv = adaptTerm(term.lhv)
                val rhv = adaptTerm(term.rhv)
                val newType = mergeTypes(types, lhv.type, rhv.type)
                term { lhv.apply(newType, term.opcode, rhv) }
            }

            else -> term
        }
    }

    override fun transformCmpTerm(term: CmpTerm): Term = when {
        term.lhv.type == term.rhv.type -> term
        term.lhv.type is KexInteger || term.rhv.type is KexInteger -> {
            val lhv = adaptTerm(term.lhv)
            val rhv = adaptTerm(term.rhv)
            term { lhv.apply(term.opcode, rhv) }
        }

        else -> term
    }
}
