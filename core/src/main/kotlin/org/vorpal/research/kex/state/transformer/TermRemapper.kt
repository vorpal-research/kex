package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.term.*

class TermRemapper(
    private val mapping: Map<Term, Term>
) : Transformer<TermRemapper> {
    override fun transformTerm(term: Term) = mapping.getOrDefault(term, term)

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        val body = transform(term.body)
        return term { lambda(term.type, term.parameters, body) }
    }
}

class TermRenamer(
    private val suffix: String,
    private val remapping: Map<Term, Term>
) : Transformer<TermRenamer> {
    override fun transformTerm(term: Term): Term = remapping[term] ?: when (term) {
        is ValueTerm, is ArgumentTerm, is ReturnValueTerm -> term {
            value(
                term.type,
                "${term.name}.$suffix"
            )
        }
        else -> term
    }

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        val body = transform(term.body)
        return term { lambda(term.type, term.parameters, body) }
    }
}
