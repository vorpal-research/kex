package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.ChoiceState
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kthelper.collection.mapNotNullTo
import org.vorpal.research.kthelper.logging.log

class Unifier : Transformer<Unifier>, IncrementalTransformer {
    private var values = mutableMapOf<Term, Term>()

    override fun apply(ps: PredicateState): PredicateState {
        val filtered = super.apply(ps)
        return TermRemapper(values).apply(filtered).also {
            log.error("Reduced state size from ${ps.size} to ${it.size}")
        }
    }

    override fun transformChoiceState(ps: ChoiceState): PredicateState {
        val oldValues = values
        val newValues = mutableSetOf<Map<Term, Term>>()
        val allKeys = mutableSetOf<Term>()
        for (choice in ps.choices) {
            values = oldValues.toMutableMap()
            transform(choice)
            newValues += values
            allKeys += values.keys
        }
        values = allKeys.mapNotNullTo(mutableMapOf()) { key ->
            newValues.mapTo(mutableSetOf()) { it[key] }.singleOrNull()?.let {
                key to it
            }
        }

        return super.transformChoiceState(ps)
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        if (predicate.type !is PredicateType.State) return predicate

        if (predicate.lhv.isValue && predicate.rhv.isValue) {
            values[predicate.lhv] = values.getOrDefault(predicate.rhv, predicate.rhv)
            return nothing()
        }

        return super.transformEqualityPredicate(predicate)
    }

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState = IncrementalPredicateState(
        apply(state.state),
        state.queries
    )
}
