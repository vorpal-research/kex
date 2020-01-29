package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term

class TermCollector(val filter: (Term) -> Boolean) : Transformer<TermCollector> {
    companion object {
        fun getFullTermSet(ps: PredicateState): Set<Term> {
            val tc = TermCollector { true }
            tc.transform(ps)
            return tc.terms
        }

        fun getFullTermSet(p: Predicate): Set<Term> {
            val tc = TermCollector { true }
            tc.transform(p)
            return tc.terms
        }

        fun getFullTermSet(t: Term): Set<Term> {
            val tc = TermCollector { true }
            tc.transform(t)
            return tc.terms
        }
    }

    val terms = hashSetOf<Term>()

    override fun transformTerm(term: Term): Term {
        if (filter(term)) terms.add(term)
        return super.transformTerm(term)
    }
}

class PredicateTermCollector(val type: PredicateType) : Transformer<PredicateTermCollector> {
    val terms = hashSetOf<Term>()

    override fun transformPredicate(predicate: Predicate): Predicate {
        if (predicate.type == type) {
            terms += TermCollector.getFullTermSet(predicate)
        }
        return predicate
    }
}

fun collectPredicateTypeTerms(type: PredicateType, state: PredicateState): Set<Term> {
    val collector = PredicateTermCollector(type)
    collector.apply(state)
    return collector.terms.filter { it.isVariable }.toSet()
}

fun collectRequiredTerms(state: PredicateState) = collectPredicateTypeTerms(PredicateType.Require(), state)
fun collectAssumedTerms(state: PredicateState) = collectPredicateTypeTerms(PredicateType.Assume(), state)