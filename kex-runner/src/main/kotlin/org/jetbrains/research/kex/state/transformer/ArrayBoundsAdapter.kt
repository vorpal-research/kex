package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.isArray
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.basic
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.ArrayIndexTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log
import java.util.*

class ArrayBoundsAdapter : RecollectingTransformer<ArrayBoundsAdapter> {
    override val builders = ArrayDeque<StateBuilder>().apply { add(StateBuilder()) }
    private var indices = setOf<ArrayIndexTerm>()
    private val arrays = mutableSetOf<Term>()

    override fun apply(ps: PredicateState): PredicateState {
        val (`this`, args) = collectArguments(ps)
        listOfNotNull(`this`, *args.values.toTypedArray()).forEach {
            addArray(it)
        }
        val res = super.apply(ps)
        return res + adaptArrays()
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldIndices = indices.toSet()
        val newChoices = arrayListOf<PredicateState>()
        val choiceAnnotatedTerms = arrayListOf<Set<ArrayIndexTerm>>()
        val choiceAnnotatedArrays = arrayListOf<Set<Term>>()
        for (choice in ps) {
            builders.add(StateBuilder())
            indices = oldIndices.toHashSet()

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
        return ps
    }

    override fun transformBase(predicate: Predicate): Predicate {
        if (predicate.hasReceiver) {
            when (predicate) {
                is NewArrayPredicate -> {
                    val dimensions = predicate.dimensions
                    if (dimensions.size > 1) log.warn("Unexpected number of dimensions in new array $predicate")
                    val length = dimensions.first()
                    if (length !is ConstIntTerm) addArray(predicate.lhv)
                }
                is GenerateArrayPredicate -> {
                    val length = predicate.length
                    if (length !is ConstIntTerm) addArray(predicate.lhv)
                }
                else -> {
                    addArray(predicate.receiver!!)
                }
            }
        }

        TermCollector.getFullTermSet(predicate)
            .filterIsInstance<ArrayIndexTerm>()
            .filter { it !in indices }
            .toSet()
            .forEach { adaptIndexTerm(it) }
        return super.transformBase(predicate)
    }

    private fun addArray(term: Term) {
        if (term.type.isArray) arrays += term
    }

    private fun adaptArrays() = basic {
        for (term in arrays) {
            ktassert(term.type is KexArray)
            assume { (term.length() ge 0) equality true }
            assume { (term.length() lt 1000) equality true }
        }
    }

    private fun adaptIndexTerm(index: ArrayIndexTerm) {
        val zero = term { const(0) }
        val length = term { index.arrayRef.length() }
        currentBuilder += assume { (zero le index.index) equality true }
        currentBuilder += assume { (index.index lt length) equality true }
        indices = indices + index
    }

}