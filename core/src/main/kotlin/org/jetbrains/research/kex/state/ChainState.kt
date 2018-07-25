package org.jetbrains.research.kex.state

import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.util.defaultHashCode

class ChainState(val base: PredicateState, val curr: PredicateState) : PredicateState() {
    override fun print(): String {
        val sb = StringBuilder()
        sb.append(base.print())
        sb.append(" -> ")
        sb.append(curr.print())
        return sb.toString()
    }

    override fun fmap(transform: (PredicateState) -> PredicateState) = ChainState(transform(base), transform(curr))
    override fun reverse() = ChainState(curr.reverse(), base.reverse())

    override val size get() = base.size + curr.size

    override fun hashCode() = defaultHashCode(base, curr)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as ChainState
        return this.base == other.base && this.curr == other.curr
    }

    override fun addPredicate(predicate: Predicate) = ChainState(base, curr + predicate)

    override fun sliceOn(state: PredicateState): PredicateState? {
        if (this == state) return BasicState()
        if (base == state) return curr
        val baseSlice = base.sliceOn(state)
        if (baseSlice != null) return ChainState(baseSlice, curr)
        if (state is ChainState && state.base == base) return curr.sliceOn(state.curr)
        return null
    }

    override fun simplify(): PredicateState {
        val sbase = base.simplify()
        val scurr = curr.simplify()
        return when {
            sbase.isNotEmpty && scurr.isNotEmpty -> ChainState(base, curr)
            sbase.isNotEmpty -> sbase
            scurr.isNotEmpty -> scurr
            else -> BasicState()
        }
    }
}