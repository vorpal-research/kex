package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.*

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

class StringTermCollector(val collectTypeNames: Boolean) : Transformer<StringTermCollector> {
    val strings = mutableSetOf<ConstStringTerm>()

    override fun transformField(term: FieldTerm): Term {
        val owner = transform(term.owner)
        return term { owner.field((term.type as KexReference).reference, term.fieldName) }
    }

    override fun transformConstClass(term: ConstClassTerm): Term {
        strings += term { const(term.constantType.javaName) } as ConstStringTerm
        return super.transformConstClass(term)
    }

    override fun transform(term: Term): Term {
        if (term is ConstStringTerm) strings += term
        if (collectTypeNames && term.type !is KexNull)
            strings += term { const(term.type.javaName) } as ConstStringTerm
        return super.transform(term)
    }
}

fun collectStringTerms(state: PredicateState, collectTypeNames: Boolean = false): Set<ConstStringTerm> {
    val stringCollector = StringTermCollector(collectTypeNames)
    stringCollector.apply(state)
    if (collectTypeNames && stringCollector.strings.isNotEmpty()) {
        stringCollector.strings += term { const(KexString().javaName) } as ConstStringTerm
        stringCollector.strings += term { const(KexChar().asArray().javaName) } as ConstStringTerm
        stringCollector.strings += term { const(KexChar().javaName) } as ConstStringTerm
        stringCollector.strings += term { const(KexInt().javaName) } as ConstStringTerm
    }
    return stringCollector.strings
}

fun collectTerms(state: PredicateState, predicate: (Term) -> Boolean): Set<Term> {
    val tc = TermCollector(predicate)
    tc.apply(state)
    return tc.terms
}