package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.term.ArrayIndexTerm
import org.jetbrains.research.kex.state.term.term
import java.util.*

class ArrayBoundsAdapter : RecollectingTransformer<ArrayBoundsAdapter> {
    override val builders = ArrayDeque<StateBuilder>().apply { add(StateBuilder()) }
    private var indices = setOf<ArrayIndexTerm>()

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldIndices = indices.toSet()
        val newChoices = arrayListOf<PredicateState>()
        val choiceAnnotatedTerms = arrayListOf<Set<ArrayIndexTerm>>()
        for (choice in ps) {
            builders.add(StateBuilder())
            indices = oldIndices.toHashSet()

            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
            choiceAnnotatedTerms.add(indices.toSet())
            builders.pollLast()
        }
        currentBuilder += newChoices
        indices = choiceAnnotatedTerms.asSequence()
                .flatten()
                .toSet()
                .filter { term -> choiceAnnotatedTerms.all { term in it } }
                .toHashSet()
        return ps
    }

    override fun transformBase(predicate: Predicate): Predicate {
        val indexTerms = TermCollector.getFullTermSet(predicate)
                .filterIsInstance<ArrayIndexTerm>()
                .filter { it !in indices }
                .toSet()
        for (index in indexTerms) {
            val zero = term { const(0) }
            val length = term { index.arrayRef.length() }
            currentBuilder += assume { (zero le index.index) equality true }
            currentBuilder += assume { (index.index lt length) equality true }
            indices = indices + index
        }
        return super.transformBase(predicate)
    }

}