package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.ArrayStorePredicate
import org.jetbrains.research.kex.state.predicate.FieldInitializerPredicate
import org.jetbrains.research.kex.state.predicate.FieldStorePredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*

class PointerCollector : Transformer<PointerCollector> {
    val ptrs = linkedSetOf<Term>()

    override fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate {
        ptrs.add(predicate.arrayRef)
        return predicate
    }

    override fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate {
        ptrs.add(predicate.field)
        return super.transformFieldInitializerPredicate(predicate)
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        ptrs.add(predicate.field)
        return predicate
    }

    override fun transformArgument(term: ArgumentTerm): Term {
        if (term.type is KexPointer) {
            ptrs += term
        }
        return super.transformArgument(term)
    }

    override fun transformArrayIndex(term: ArrayIndexTerm): Term {
        ptrs.add(term.arrayRef)
        return term
    }

    override fun transformArrayLoadTerm(term: ArrayLoadTerm): Term {
        ptrs.add(term.arrayRef)
        return term
    }

    override fun transformArrayLengthTerm(term: ArrayLengthTerm): Term {
        ptrs.add(term.arrayRef)
        return term
    }

    override fun transformField(term: FieldTerm): Term {
        ptrs.add(term.owner)
        return term
    }

    override fun transformFieldLoadTerm(term: FieldLoadTerm): Term {
        ptrs.add(term.field)
        return term
    }

    override fun transformConstClassTerm(term: ConstClassTerm): Term {
        ptrs.add(term)
        return term
    }

    override fun apply(ps: PredicateState): PredicateState {
        ptrs.clear()
        return super.apply(ps)
    }
}

fun collectPointers(ps: PredicateState): Set<Term> {
    val collector = PointerCollector()
    collector.apply(ps)
    return collector.ptrs.toSet()
}