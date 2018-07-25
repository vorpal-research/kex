package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.Term

class Slicer(val state: PredicateState, val query: PredicateState) : Transformer<Slicer> {
    val aa = AliasAnalyzer()
    val silceVars = hashSetOf<Term>()
    val slicePtrs = hashSetOf<Term>()


    init {
        aa.transform(state)
    }

}