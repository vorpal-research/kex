package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.term

class ConcolicFilter : Transformer<ConcolicFilter> {
    override fun transformEquality(predicate: EqualityPredicate): Predicate {
        if (predicate.lhv == term { const(true) } && predicate.rhv == term { const(true) })
            return nothing()
        return super.transformEquality(predicate)
    }
}