package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import java.util.*

object RandomSlicer : Transformer<RandomSlicer> {
    private val random = Random(17)
    private var size = 0

    override fun transform(ps: PredicateState): PredicateState {
        size = ps.size
        return super.transform(ps).simplify()
    }

    override fun transformBase(predicate: Predicate): Predicate = when {
        random.nextDouble() < (2.0 / size)-> nothing()
        else -> predicate
    }
}