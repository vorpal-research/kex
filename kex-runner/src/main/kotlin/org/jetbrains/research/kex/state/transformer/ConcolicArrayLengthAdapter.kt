package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.isArray
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kthelper.collection.dequeOf

class ConcolicArrayLengthAdapter : RecollectingTransformer<ConcolicArrayLengthAdapter> {
    override val builders = dequeOf(StateBuilder())

    override fun apply(ps: PredicateState): PredicateState {
        val arrays = TermCollector { it.type.isArray }.let {
            it.apply(ps)
            it.terms
        }
        super.apply(ps)
        for (array in arrays) {
            currentBuilder.assume { (array.length() le 1000) equality true }
        }
        return state.simplify()
    }
}