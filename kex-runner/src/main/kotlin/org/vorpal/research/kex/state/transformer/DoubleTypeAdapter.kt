package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.assume
import org.vorpal.research.kex.state.predicate.hasReceiver
import org.vorpal.research.kex.state.predicate.receiver
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kthelper.collection.dequeOf

class DoubleTypeAdapter : RecollectingTransformer<DoubleTypeAdapter> {
    override val builders = dequeOf(StateBuilder())

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
