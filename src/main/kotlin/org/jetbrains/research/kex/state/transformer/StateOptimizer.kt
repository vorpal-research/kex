package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChainState
import org.jetbrains.research.kex.state.PredicateState

class StateOptimizer : Transformer<StateOptimizer> {
    private val cache = mutableMapOf<Pair<PredicateState, PredicateState>, PredicateState?>()

    override fun transformChainState(ps: ChainState): PredicateState {
        var merged = merge(ps.base, ps.curr)
        val res = when {
            merged != null -> merged
            ps.base is ChainState && ps.curr is BasicState -> {
                merged = merge(ps.base.curr, ps.curr)
                if (merged != null) transformChainState(ChainState(ps.base.base, merged))
                else null
            }
            ps.base is BasicState && ps.curr is ChainState -> {
                merged = merge(ps.base, ps.curr.base)
                if (merged != null) transformChainState(ChainState(merged, ps.curr.curr))
                else null
            }
            else -> null
        }
        return if (res == null) ps else res
    }

    fun merge(first: PredicateState, second: PredicateState): PredicateState? {
        val key = first to second
        val m = cache[key]
        return when {
            m != null -> m
            first is BasicState && second is BasicState -> {
                val merged = BasicState(first.predicates() + second.predicates())
                cache[key] = merged
                merged
            }
            else -> cache.getOrPut(key) { null }
        }
    }
}