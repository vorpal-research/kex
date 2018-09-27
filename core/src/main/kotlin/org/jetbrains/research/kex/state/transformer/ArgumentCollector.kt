package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.ArgumentTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.ValueTerm

object ArgumentCollector : Transformer<ArgumentCollector> {
    private var arguments = hashMapOf<Int, ArgumentTerm>()
    private var thisTerm: ValueTerm? = null

    override fun transformValueTerm(term: ValueTerm): Term {
        if (term.name == "this") {
            thisTerm = term
        }
        return term
    }

    override fun transformArgumentTerm(term: ArgumentTerm): Term {
        arguments[term.index] = term
        return term
    }

    override fun apply(ps: PredicateState): PredicateState {
        arguments.clear()
        thisTerm = null
        return super.apply(ps)
    }

    operator fun invoke(ps: PredicateState): Pair<ValueTerm?, Map<Int, ArgumentTerm>> {
        apply(ps)
        return thisTerm to arguments.toMap()
    }
}