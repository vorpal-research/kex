package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.isArray
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.assume
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kthelper.collection.dequeOf

class ConcolicArrayLengthAdapter : IncrementalTransformer, RecollectingTransformer<ConcolicArrayLengthAdapter> {
    override val builders = dequeOf(StateBuilder())
    private val maxArrayLength = kexConfig.getIntValue("testGen", "maxArrayLength", 1000)

    private fun collectArrays(state: PredicateState): Set<Term> = TermCollector { it.type.isArray }.let {
        it.apply(state)
        it.terms
    }

    override fun apply(ps: PredicateState): PredicateState {
        val arrays = collectArrays(ps)
        val state = super.apply(ps).builder()
        for (array in arrays) {
            state.assume { (array.length() le maxArrayLength) equality true }
        }
        return state.apply().simplify()
    }

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        val baseArrays = collectArrays(state.state)

        return IncrementalPredicateState(
            state.state,
            state.queries.map { query ->
                val arrays = baseArrays + collectArrays(query.hardConstraints)
                var softConstraints = query.softConstraints
                for (array in arrays) {
                    softConstraints = softConstraints.add(assume {
                        (array.length() le maxArrayLength) equality true
                    })
                }
                PredicateQuery(query.hardConstraints, softConstraints)
            }
        )
    }
}
