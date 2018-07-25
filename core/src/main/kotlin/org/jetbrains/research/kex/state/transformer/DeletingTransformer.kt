package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate

interface DeletingTransformer<T> : Transformer<DeletingTransformer<T>> {
    val removablePredicates: MutableSet<Predicate>

    override fun transform(ps: PredicateState): PredicateState {
        val result = super.transform(ps)
        return result.filter { it in removablePredicates }.simplify()
    }
}