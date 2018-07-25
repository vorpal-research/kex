package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChainState
import org.jetbrains.research.kex.state.PredicateState

class Optimizer : Transformer<Optimizer> {
    private val cache = hashMapOf<Pair<PredicateState, PredicateState>, PredicateState?>()

    override fun transformChainState(ps: ChainState): PredicateState {
        var merged = merge(ps.base, ps.curr)
        return when {
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
        } ?: ps
    }

    fun merge(first: PredicateState, second: PredicateState): PredicateState? {
        val key = first to second
        val m = cache[key]
        return when {
            m != null -> m
            first is BasicState && second is BasicState -> {
                val merged = BasicState(first.predicates + second.predicates)
                cache[key] = merged
                merged
            }
            else -> cache.getOrPut(key) { null }
        }
    }
}