package org.jetbrains.research.kex.state

import org.jetbrains.research.kex.state.predicate.Predicate

class StateBuilder() {
    var current: PredicateState = BasicState()

    constructor(state: PredicateState) : this() {
        this.current = state
    }

    operator fun plus(predicate: Predicate): StateBuilder {
        return StateBuilder(current + predicate)
    }

    operator fun plusAssign(predicate: Predicate) {
        current += predicate
    }

    operator fun plus(state: PredicateState) =
            if (state.isEmpty()) this
            else if (current.isEmpty()) StateBuilder(state)
            else StateBuilder(ChainState(current, state))

    operator fun plusAssign(state: PredicateState) {
        current = ChainState(current, state)
    }

    fun apply() = current
}

abstract class PredicateState {
    abstract fun print(): String

    override fun toString() = print()

    abstract fun map(transform: (Predicate) -> Predicate): PredicateState
    abstract fun mapNotNull(transform: (Predicate) -> Predicate?): PredicateState
    abstract fun filter(predicate: (Predicate) -> Boolean): PredicateState
    fun filterNot(predicate: (Predicate) -> Boolean) = filter { it -> !predicate(it) }
    abstract fun reverse(): PredicateState

    abstract fun size(): Int
    fun isEmpty() = size() == 0
    fun isNotEmpty() = !isEmpty()

    abstract fun addPredicate(predicate: Predicate): PredicateState
    operator fun plus(predicate: Predicate) = addPredicate(predicate)

    abstract fun sliceOn(state: PredicateState): PredicateState?
}