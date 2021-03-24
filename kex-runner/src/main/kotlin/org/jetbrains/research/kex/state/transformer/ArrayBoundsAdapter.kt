package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.ArrayIndexTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import java.util.*

class ArrayBoundsAdapter : RecollectingTransformer<ArrayBoundsAdapter> {
    override val builders = ArrayDeque<StateBuilder>().apply { add(StateBuilder()) }
    private var indices = setOf<ArrayIndexTerm>()
    private var arrays = setOf<Term>()

    override fun apply(ps: PredicateState): PredicateState {
        val (`this`, args) = collectArguments(ps)
        listOfNotNull(`this`, *args.values.toTypedArray()).forEach {
            adaptArray(it)
        }
        return super.apply(ps)
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldIndices = indices.toSet()
        val oldArrays = arrays.toSet()
        val newChoices = arrayListOf<PredicateState>()
        val choiceAnnotatedTerms = arrayListOf<Set<ArrayIndexTerm>>()
        val choiceAnnotatedArrays = arrayListOf<Set<Term>>()
        for (choice in ps) {
            builders.add(StateBuilder())
            indices = oldIndices.toHashSet()
            arrays = oldArrays.toHashSet()

            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
            choiceAnnotatedTerms.add(indices.toSet())
            choiceAnnotatedArrays.add(arrays.toSet())
            builders.pollLast()
        }
        currentBuilder += newChoices
        indices = choiceAnnotatedTerms.flatten().toSet()
                .filter { term -> choiceAnnotatedTerms.all { term in it } }
                .toHashSet()
        arrays = choiceAnnotatedArrays.flatten().toSet()
                .filter { term -> choiceAnnotatedArrays.all { term in it } }
                .toHashSet()
        return ps
    }

    override fun transformBase(predicate: Predicate): Predicate {
        if (predicate.hasReceiver && predicate !is NewArrayPredicate) adaptArray(predicate.receiver!!)

        TermCollector.getFullTermSet(predicate)
                .filterIsInstance<ArrayIndexTerm>()
                .filter { it !in indices }
                .toSet()
                .forEach { adaptIndexTerm(it) }
        return super.transformBase(predicate)
    }

    private fun adaptArray(term: Term) {
        if (term.type is KexArray && term !in arrays) {
            currentBuilder += assume { (term.length() lt 1000) equality true }
            arrays = arrays + term
        }
    }

    private fun adaptIndexTerm(index: ArrayIndexTerm) {
        val zero = term { const(0) }
        val length = term { index.arrayRef.length() }
        if (index.arrayRef !in arrays) {
            currentBuilder += assume { (length lt 1000) equality true }
            arrays = arrays + index.arrayRef
        }
        if (index.index !is ConstIntTerm) {
            currentBuilder += assume { (zero le index.index) equality true }
            currentBuilder += assume { (index.index lt length) equality true }
        }
        indices = indices + index
    }

}