package org.jetbrains.research.kex.state

import org.jetbrains.research.kex.state.predicate.Predicate

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
}