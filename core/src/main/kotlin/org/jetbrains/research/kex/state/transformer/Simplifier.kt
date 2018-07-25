package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.ChainState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.PredicateType

class Simplifier : Transformer<Simplifier> {
    override fun transformChoiceState(ps: ChoiceState): PredicateState {
        val choices = ps.choices.map { it.filterByType(PredicateType.Path()) }.toSet()
        return if (choices.size == 1) choices.first() else ps
    }

    override fun transformChainState(ps: ChainState) = when {
        ps.base.isEmpty -> ps.curr
        ps.curr.isEmpty -> ps.base
        else -> ps
    }
}