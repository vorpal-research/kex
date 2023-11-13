package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.ReturnValueTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ValueTerm
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.StateClause
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState

class TermRemapper(
    private val mapping: Map<Term, Term>
) : Transformer<TermRemapper> {
    override fun transformTerm(term: Term) = mapping.getOrDefault(term, term)

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        val body = transform(term.body)
        return term { lambda(term.type, term.parameters, body) }
    }
}
class SymbolicStateTermRemapper(
    mapping: Map<Term, Term>
) {
    private val mapper = TermRemapper(mapping)
    fun apply(state: SymbolicState): PersistentSymbolicState {
        var result = persistentSymbolicState()
        for (clause in state.clauses) {
            val transformed = mapper.transform(clause.predicate)
            result = result.copy(
                clauses = result.clauses + StateClause(clause.instruction, transformed)
            )
        }
        for (clause in state.path) {
            val transformed = mapper.transform(clause.predicate)
            result = result.copy(
                path = result.path + PathClause(clause.type, clause.instruction, transformed)
            )
        }
        return result
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
