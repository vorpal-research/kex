package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.term.Term

class Unifier : Transformer<Unifier>, IncrementalTransformer {
    private var values = linkedMapOf<Term, MutableSet<Term>>()

    override fun apply(ps: PredicateState): PredicateState {
        super.apply(ps)
        val filtered = ps.filterNot { predicate ->
            predicate.type is PredicateType.State
                    && predicate is EqualityPredicate
                    && predicate.lhv.isValue
                    && predicate.rhv.isValue
                    && values.getOrDefault(predicate.lhv, emptySet()).size == 1
        }
        val mappings = buildMap {
            for ((term, candidates) in this@Unifier.values) {
                val candidate = candidates.singleOrNull() ?: continue
                this[term] = this.getOrDefault(candidate, candidate)
            }
        }
        return TermRemapper(mappings).apply(filtered)
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        if (predicate.lhv.isValue && predicate.rhv.isValue) {
            values.getOrPut(predicate.lhv, ::mutableSetOf) += predicate.rhv
        }
        return super.transformEqualityPredicate(predicate)
    }

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState = IncrementalPredicateState(
        apply(state.state),
        state.queries
    )
}
