package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.isConst

class BasicFilter : Transformer<BasicFilter> {
    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate =
        when {
            predicate.lhv.isConst && predicate.rhv.isConst -> nothing()
            else -> super.transformEqualityPredicate(predicate)
        }
}
