package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState

class Transformation : Transformer<Transformation> {
    private val transformers = mutableListOf<(PredicateState) -> PredicateState>()

    override fun apply(ps: PredicateState): PredicateState {
        var state = ps
        for (transformer in transformers) {
            state = transformer.invoke(state)
        }
        return state
    }

    operator fun Transformer<*>.unaryPlus() {
        transformers += { state -> this.apply(state) }
    }

    operator fun ((PredicateState) -> PredicateState).unaryPlus() {
        transformers += this
    }
}

fun transform(state: PredicateState, body: Transformation.() -> Unit): PredicateState {
    val transformer = Transformation()
    transformer.body()
    return transformer.apply(state)
}

class IncrementalTransformation : IncrementalTransformer {
    private val transformers = mutableListOf<IncrementalTransformer>()

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun apply(ps: IncrementalPredicateState): IncrementalPredicateState {
        var state = ps
        for (transformer in transformers) {
            state = transformer.apply(state)
        }
        return state
    }

    operator fun IncrementalTransformer.unaryPlus() {
        transformers += this
    }
}

fun transformIncremental(
    state: IncrementalPredicateState,
    body: IncrementalTransformation.() -> Unit
): IncrementalPredicateState {
    val transformer = IncrementalTransformation()
    transformer.body()
    return transformer.apply(state)
}
