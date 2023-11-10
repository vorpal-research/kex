package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.ArrayInitializerPredicate
import org.vorpal.research.kex.state.predicate.ArrayStorePredicate
import org.vorpal.research.kex.state.predicate.FieldInitializerPredicate
import org.vorpal.research.kex.state.predicate.FieldStorePredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ArrayLengthTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.ClassAccessTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ValueTerm

class LambdaParametersCollector : Transformer<PointerCollector> {
    val lambdaParams = mutableSetOf<Term>()
    private var inLambda = false

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        inLambda = true
        lambdaParams += term.parameters
        val res = super.transformLambdaTerm(term)
        inLambda = false
        return res
    }

    override fun transformTerm(term: Term): Term {
        if (!inLambda) return term
        if (term in lambdaParams || term.subTerms.any { it in lambdaParams }) {
            lambdaParams += term
        }
        return term
    }
}

class PointerCollector : Transformer<PointerCollector> {
    val ptrs = linkedSetOf<Term>()

    override fun transformArrayInitializerPredicate(predicate: ArrayInitializerPredicate): Predicate {
        ptrs.add(predicate.arrayRef)
        return predicate
    }

    override fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate {
        ptrs.add(predicate.arrayRef)
        return predicate
    }

    override fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate {
        ptrs.add(predicate.field)
        return predicate
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        ptrs.add(predicate.field)
        return predicate
    }

    override fun transformArgument(term: ArgumentTerm): Term {
        if (term.type is KexPointer) {
            ptrs += term
        }
        return term
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

    override fun transformClassAccessTerm(term: ClassAccessTerm): Term {
        ptrs.add(term)
        return term
    }

    override fun transformConstClassTerm(term: ConstClassTerm): Term {
        ptrs.add(term)
        return term
    }

    override fun transformValueTerm(term: ValueTerm): Term {
        if (term.type is KexPointer) {
            ptrs.add(term)
        }
        return term
    }

    override fun apply(ps: PredicateState): PredicateState {
        ptrs.clear()
        return super.apply(ps)
    }
}

fun collectPointers(ps: PredicateState, ignoreLambdaParams: Boolean = false): Set<Term> {
    val collector = PointerCollector()
    collector.apply(ps)
    val ptrs = collector.ptrs
    return ptrs - when {
        ignoreLambdaParams -> LambdaParametersCollector().also { it.apply(ps) }.lambdaParams
        else -> emptySet()
    }
}
