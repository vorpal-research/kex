package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.ChoiceState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.assume
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kthelper.collection.dequeOf

class NullityAnnotator(private val nonNulls: Set<Term> = setOf()) : RecollectingTransformer<NullityAnnotator> {
    override val builders = dequeOf(StateBuilder())
    private var annotatedTerms = hashSetOf<Term>()

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldAnnotatedTerms = annotatedTerms.toSet()
        val newChoices = arrayListOf<PredicateState>()
        val choiceAnnotatedTerms = arrayListOf<Set<Term>>()
        for (choice in ps) {
            builders.add(StateBuilder())
            annotatedTerms = oldAnnotatedTerms.toHashSet()

            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
            choiceAnnotatedTerms.add(annotatedTerms.toSet())
            builders.pollLast()
        }
        currentBuilder += newChoices
        annotatedTerms = choiceAnnotatedTerms.asSequence()
            .flatten()
            .toSet()
            .filter { term -> choiceAnnotatedTerms.all { term in it } }
            .toHashSet()
        return ps
    }

    override fun transformBase(predicate: Predicate): Predicate {
        currentBuilder += predicate
        val predicateTerms = TermCollector.getFullTermSet(predicate)
            .asSequence()
            .filter { it in nonNulls }
            .filter { it !in annotatedTerms }
            .toSet()
        for (term in predicateTerms) {
            currentBuilder += assume { term inequality null }
            annotatedTerms.add(term)
        }
        return predicate
    }

}