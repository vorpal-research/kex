package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexDouble
import org.jetbrains.research.kex.ktype.KexFloat
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.predicate.hasReceiver
import org.jetbrains.research.kex.state.predicate.receiver
import org.jetbrains.research.kex.state.term.Term
import java.util.*

class DoubleTypeAdapter : RecollectingTransformer<DoubleTypeAdapter> {
    override val builders = ArrayDeque<StateBuilder>()

    init {
        builders.push(StateBuilder())
    }

    private fun addFPContract(terms: Set<Term>) {
        terms.forEach {
            when {
                it.type is KexFloat -> {
                    currentBuilder += pf.getInequality(it, tf.getFloat(Float.NaN), PredicateType.Assume())
                }
                it.type is KexDouble -> {
                    currentBuilder += pf.getInequality(it, tf.getDouble(Double.NaN), PredicateType.Assume())
                }
            }
        }
    }

    override fun transformBase(predicate: Predicate): Predicate {
        currentBuilder += predicate
        if (predicate.hasReceiver) {
            addFPContract(setOf(predicate.receiver!!))
        }
        return predicate
    }

    override fun apply(ps: PredicateState): PredicateState {
        val (`this`, args) = collectArguments(ps)
        val fpArgs = when {
            `this` != null -> args.values + `this`
            else -> args.values
        }.toSet()
        addFPContract(fpArgs)
        return super.apply(ps)
    }

}