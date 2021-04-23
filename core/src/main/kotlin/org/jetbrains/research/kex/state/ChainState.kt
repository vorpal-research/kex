package org.jetbrains.research.kex.state

import org.jetbrains.research.kthelper.defaultHashCode
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.state.predicate.Predicate

@InheritorOf("State")
@Serializable
class ChainState(val base: PredicateState, val curr: PredicateState) : PredicateState() {
    override val size: Int
        get() = base.size + curr.size

    override fun print() = buildString {
        append(base.print())
        append(" -> ")
        append(curr.print())
    }

    override fun fmap(transform: (PredicateState) -> PredicateState) = ChainState(transform(base), transform(curr))
    override fun reverse() = ChainState(curr.reverse(), base.reverse())

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
            sbase.isNotEmpty && scurr.isNotEmpty -> (sbase.builder() + scurr).apply()
            sbase.isNotEmpty -> sbase
            scurr.isNotEmpty -> scurr
            else -> emptyState()
        }
    }
}