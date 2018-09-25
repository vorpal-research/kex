package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.*

object VariableCollector : Transformer<VariableCollector> {
    private val variables = hashSetOf<Term>()

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

    override fun apply(ps: PredicateState): PredicateState {
        variables.clear()
        return super.apply(ps)
    }

    operator fun invoke(ps: PredicateState): Set<Term> {
        apply(ps)
        return variables.toSet()
    }
}