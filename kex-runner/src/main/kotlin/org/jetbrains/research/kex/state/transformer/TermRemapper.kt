package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.term.Term

class TermRemapper(val mapping: Map<Term, Term>) : Transformer<TermRemapper> {
    override fun transformTerm(term: Term) = mapping.getOrDefault(term, term)
}