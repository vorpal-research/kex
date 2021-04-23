package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.term.*

class NullityInfoAdapter : RecollectingTransformer<NullityInfoAdapter> {
    override val builders = dequeOf(StateBuilder())
    private var annotatedTerms = setOf<Term>()

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldAnnotatedTerms = annotatedTerms.toSet()
        val newChoices = arrayListOf<PredicateState>()
        val choiceAnnotatedTerms = arrayListOf<Set<Term>>()
        for (choice in ps) {
            builders.add(StateBuilder())
            annotatedTerms = oldAnnotatedTerms.toSet()

            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
            choiceAnnotatedTerms.add(annotatedTerms.toSet())
            builders.pollLast()
        }
        currentBuilder += newChoices
        annotatedTerms = choiceAnnotatedTerms.flatten().toSet()
                .filter { term -> choiceAnnotatedTerms.all { term in it } }
                .toSet()
        return ps
    }

    override fun transformFieldTerm(term: FieldTerm): Term {
        if (!term.isStatic && term.owner !in annotatedTerms) {
            currentBuilder += assume { term.owner inequality null }
            annotatedTerms = annotatedTerms + term.owner
        }
        return super.transformFieldTerm(term)
    }

    override fun transformArrayIndexTerm(term: ArrayIndexTerm): Term {
        if (term.arrayRef !in annotatedTerms) {
            currentBuilder += assume { term.arrayRef inequality null }
            annotatedTerms = annotatedTerms + term.arrayRef
        }
        return super.transformArrayIndexTerm(term)
    }

    override fun transformConstClassTerm(term: ConstClassTerm): Term {
        if (term !in annotatedTerms) {
            currentBuilder += assume { term inequality null }
            annotatedTerms = annotatedTerms + term
        }
        return super.transformConstClassTerm(term)
    }

    override fun transformCallTerm(term: CallTerm): Term {
        if (!term.isStatic && term.owner !in annotatedTerms) {
            currentBuilder += assume { term.owner inequality null }
            annotatedTerms = annotatedTerms + term.owner
        }
        return super.transformCallTerm(term)
    }
}