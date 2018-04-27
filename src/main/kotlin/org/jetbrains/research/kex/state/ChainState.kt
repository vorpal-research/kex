package org.jetbrains.research.kex.state

import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kfg.util.defaultHashCode

class ChainState(val base: PredicateState, val curr: PredicateState) : PredicateState() {
    override fun print(): String {
        val sb = StringBuilder()
        sb.append(base.print())
        sb.append(" -> ")
        sb.append(curr.print())
        return sb.toString()
    }

    override fun map(transform: (Predicate) -> Predicate) = ChainState(base.map(transform), curr.map(transform))
    override fun mapNotNull(transform: (Predicate) -> Predicate?) = ChainState(base.mapNotNull(transform), curr.mapNotNull(transform))
    override fun filter(predicate: (Predicate) -> Boolean) = ChainState(base.filter(predicate), curr.filter(predicate))
    override fun reverse() = ChainState(curr.reverse(), base.reverse())

    override fun size() = base.size() + curr.size()

    override fun hashCode() = defaultHashCode(base, curr)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as ChainState
        return this.base == other.base && this.curr == other.curr
    }
}