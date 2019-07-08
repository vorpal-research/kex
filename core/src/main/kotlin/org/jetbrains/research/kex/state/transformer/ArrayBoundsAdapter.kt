package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.ArrayIndexTerm
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import java.util.*

class ArrayBoundsAdapter : RecollectingTransformer<ArrayBoundsAdapter> {
    override val builders = ArrayDeque<StateBuilder>()
    var indices = setOf<ArrayIndexTerm>()

    init {
        builders.add(StateBuilder())
    }

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
            val zero = tf.getInt(0)
            val length = tf.getArrayLength(index.arrayRef)
            currentBuilder += pf.getEquality(tf.getCmp(CmpOpcode.Le(), zero, index.index), tf.getTrue(), PredicateType.Assume())
            currentBuilder += pf.getEquality(tf.getCmp(CmpOpcode.Lt(), index.index, length), tf.getTrue(), PredicateType.Assume())
            indices = indices + index
        }
        return super.transformBase(predicate)
    }

}