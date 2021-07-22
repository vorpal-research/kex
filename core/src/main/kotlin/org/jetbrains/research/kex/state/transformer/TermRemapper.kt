package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.term.ArgumentTerm
import org.jetbrains.research.kex.state.term.ReturnValueTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.ValueTerm

class TermRemapper(val mapping: Map<Term, Term>) : Transformer<TermRemapper> {
    override fun transformTerm(term: Term) = mapping.getOrDefault(term, term)
}

class TermRenamer(val suffix: String, val remapping: Map<Term, Term>) : Transformer<TermRenamer> {
    override fun transformTerm(term: Term): Term = remapping[term] ?: when (term) {
        is ValueTerm, is ArgumentTerm, is ReturnValueTerm -> org.jetbrains.research.kex.state.term.term {
            value(
                term.type,
                "${term.name}.$suffix"
            )
        }
        else -> term
    }
}