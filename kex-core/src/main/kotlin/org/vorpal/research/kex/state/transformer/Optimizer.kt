package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.BasicState
import org.vorpal.research.kex.state.ChainState
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState

class Optimizer : Transformer<Optimizer>, IncrementalTransformer {
    private val cache = hashMapOf<Pair<PredicateState, PredicateState>, PredicateState?>()
    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            apply(state.state),
            state.queries.map { query ->
                PredicateQuery(
                    apply(query.hardConstraints),
                    query.softConstraints
                )
            }
        )
    }

    override fun transformChainState(ps: ChainState): PredicateState {
        val merged = merge(ps.base, ps.curr)
        return when {
            merged != null -> merged
            ps.base is ChainState && ps.curr is BasicState ->
                merge(ps.base.curr, ps.curr)?.let {
                    transformChainState(ChainState(ps.base.base, it))
                }
            ps.base is BasicState && ps.curr is ChainState ->
                merge(ps.base, ps.curr.base)?.let {
                    transformChainState(ChainState(it, ps.curr.curr))
                }
            else -> null
        } ?: ps
    }

    private fun merge(first: PredicateState, second: PredicateState): PredicateState? {
        val key = first to second
        val m = cache[key]
        return when {
            m != null -> m
            first is BasicState && second is BasicState ->
                BasicState(first.predicates + second.predicates).also {
                    cache[key] = it
                }
            first is ChainState && first.curr is BasicState && second is BasicState ->
                merge(first.curr, second)?.let { merged ->
                    ChainState(first.base, merged).also {
                        cache[key] = it
                    }
                }
            first is BasicState && second is ChainState && second.base is BasicState ->
                merge(first, second.base)?.let { merged ->
                    ChainState(merged, second.curr).also {
                        cache[key] = it
                    }
                }
            else -> cache.getOrPut(key) { null }
        }
    }
}
