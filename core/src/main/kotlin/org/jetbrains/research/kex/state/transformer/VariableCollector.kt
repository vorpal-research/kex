package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.term.ArgumentTerm
import org.jetbrains.research.kex.state.term.ReturnValueTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.ValueTerm

class VariableCollector : Transformer<VariableCollector> {
    val variables = mutableSetOf<Term>()

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
}