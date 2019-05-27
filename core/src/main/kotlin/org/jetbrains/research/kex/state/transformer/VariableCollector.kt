package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.*

class VariableCollector : Transformer<VariableCollector> {
    val variables = hashSetOf<Term>()

    override fun transformArgumentTerm(term: ArgumentTerm): Term {
        variables.add(term)
        return term
    }

    override fun transformValueTerm(term: ValueTerm): Term {
        variables.add(term)
        return term
    }

    override fun transformReturnValueTerm(term: ReturnValueTerm): Term {
        variables.add(term)
        return term
    }

    override fun transformFieldTerm(term: FieldTerm): Term {
        variables.add(term)
        return term
    }
}

fun collectVariables(ps: PredicateState): Set<Term> {
    val collector = VariableCollector()
    collector.apply(ps)
    return collector.variables.toSet()
}