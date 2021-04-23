package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.ConstStringTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term

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

class PredicateTermCollector(val filter: (Predicate) -> Boolean) : Transformer<PredicateTermCollector> {
    val terms = hashSetOf<Term>()

    override fun transformPredicate(predicate: Predicate): Predicate {
        if (filter(predicate)) {
            terms += TermCollector.getFullTermSet(predicate)
        }
        return predicate
    }
}

fun collectPredicateTypeTerms(type: PredicateType, state: PredicateState): Set<Term> {
    val collector = PredicateTermCollector { it.type == type }
    collector.apply(state)
    return collector.terms.filter { it.isVariable }.toSet()
}

fun collectPredicateTerms(state: PredicateState, filter: (Predicate) -> Boolean): Set<Term> {
    val collector = PredicateTermCollector(filter)
    collector.apply(state)
    return collector.terms.filter { it.isVariable }.toSet()
}

fun collectRequiredTerms(state: PredicateState) = collectPredicateTypeTerms(PredicateType.Require(), state)
fun collectAssumedTerms(state: PredicateState) = collectPredicateTypeTerms(PredicateType.Assume(), state)
fun collectAxiomTerms(state: PredicateState) = collectPredicateTypeTerms(PredicateType.Axiom(), state)

class StringTermCollector : Transformer<StringTermCollector> {
    val strings = mutableSetOf<ConstStringTerm>()

    override fun transformField(term: FieldTerm): Term {
        val owner = transform(term.owner)
        return term { owner.field((term.type as KexReference).reference, term.fieldName) }
    }

    override fun transform(term: Term): Term {
        if (term is ConstStringTerm) strings += term
        return super.transform(term)
    }
}

fun collectStringTerms(state: PredicateState): Set<ConstStringTerm> {
    val stringCollector = StringTermCollector()
    stringCollector.apply(state)
    return stringCollector.strings
}

fun collectTerms(state: PredicateState, predicate: (Term) -> Boolean): Set<Term> {
    val tc = TermCollector(predicate)
    tc.apply(state)
    return tc.terms
}