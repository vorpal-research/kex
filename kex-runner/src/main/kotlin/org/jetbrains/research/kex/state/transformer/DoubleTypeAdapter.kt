package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexDouble
import org.jetbrains.research.kex.ktype.KexFloat
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.predicate.hasReceiver
import org.jetbrains.research.kex.state.predicate.receiver
import org.jetbrains.research.kex.state.term.Term
import java.util.*

class DoubleTypeAdapter : RecollectingTransformer<DoubleTypeAdapter> {
    override val builders = ArrayDeque<StateBuilder>().apply { push(StateBuilder()) }

    private fun addFPContract(terms: Set<Term>) {
        terms.forEach {
            when (it.type) {
                is KexFloat -> {
                    currentBuilder += assume { it inequality Float.NaN }
                }
                is KexDouble -> {
                    currentBuilder += assume { it inequality Double.NaN }
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