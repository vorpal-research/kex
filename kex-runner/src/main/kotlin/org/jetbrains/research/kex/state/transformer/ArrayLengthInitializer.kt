package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.collection.dequeOf
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.term.Term

class ArrayLengthInitializer : RecollectingTransformer<ArrayLengthInitializer> {
    override val builders = dequeOf(StateBuilder())
    private var arrays = setOf<Term>()

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldArrays = arrays.toSet()
        val newChoices = arrayListOf<PredicateState>()
        val choiceAnnotatedArrays = arrayListOf<Set<Term>>()
        for (choice in ps) {
            builders.add(StateBuilder())
            arrays = oldArrays.toHashSet()

            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
            choiceAnnotatedArrays.add(arrays.toSet())
            builders.pollLast()
        }
        currentBuilder += newChoices
        arrays = choiceAnnotatedArrays.asSequence()
                .flatten()
                .toSet()
                .filter { term -> choiceAnnotatedArrays.all { term in it } }
                .toHashSet()
        return ps
    }

    override fun transformBase(predicate: Predicate): Predicate {
        val arrayTerms = TermCollector.getFullTermSet(predicate)
                .filter { it.type is KexArray }
                .filter { it !in arrays }
                .toSet()
        for (array in arrayTerms) {
            currentBuilder += assume { generate(KexInt()) equality array.length() }
            arrays = arrays + array
        }
        return super.transformBase(predicate)
    }

}