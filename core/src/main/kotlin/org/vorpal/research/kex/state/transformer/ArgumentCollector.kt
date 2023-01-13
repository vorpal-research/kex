package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ValueTerm

class ArgumentCollector : Transformer<ArgumentCollector> {
    val arguments = hashMapOf<Int, ArgumentTerm>()
    var thisTerm: ValueTerm? = null
        private set

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
}

fun collectArguments(ps: PredicateState): Pair<ValueTerm?, Map<Int, ArgumentTerm>> {
    val collector = ArgumentCollector()
    collector.apply(ps)
    return collector.thisTerm to collector.arguments
}
