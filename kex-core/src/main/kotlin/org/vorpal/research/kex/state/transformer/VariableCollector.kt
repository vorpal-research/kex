package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexVoid
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.ReturnValueTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ValueTerm

val Term.isVariable: Boolean get() = when (this) {
    is ArgumentTerm -> true
    is ValueTerm -> true
    is ReturnValueTerm -> true
    is FieldTerm -> true
    else -> false
}

class VariableCollector : Transformer<VariableCollector> {
    val variables = linkedSetOf<Term>()

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
    return collector.variables.filterTo(mutableSetOf()) { it.type !is KexVoid }
}
