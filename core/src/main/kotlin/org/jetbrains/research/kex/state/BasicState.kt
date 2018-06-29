package org.jetbrains.research.kex.state

import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.util.defaultHashCode

class BasicState() : PredicateState() {
    protected val predicates = mutableListOf<Predicate>()

    constructor(predicates: List<Predicate>) : this() {
        this.predicates.addAll(predicates)
    }

    fun predicates(): List<Predicate> = predicates

    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("(")
        predicates.forEach { sb.appendln("  $it") }
        sb.append(")")
        return sb.toString()
    }

    override fun map(transform: (Predicate) -> Predicate) = BasicState(predicates().map(transform))
    override fun fmap(transform: (PredicateState) -> PredicateState) = transform(this)
    override fun mapNotNull(transform: (Predicate) -> Predicate?) = BasicState(predicates().mapNotNull(transform))
    override fun filter(predicate: (Predicate) -> Boolean) = BasicState(predicates().filter(predicate))
    override fun reverse(): PredicateState = BasicState(predicates().reversed())
    override fun size() = predicates().size

    override fun hashCode() = defaultHashCode(*predicates.toTypedArray())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as BasicState
        return this.predicates.toTypedArray().contentEquals(other.predicates().toTypedArray())
    }

    override fun addPredicate(predicate: Predicate) = BasicState(predicates.plus(predicate))

    override fun sliceOn(state: PredicateState): PredicateState? = when (state) {
        is BasicState -> {
            val base = predicates().take(state.size()).toTypedArray()
            if (base.contentEquals(state.predicates().toTypedArray())) BasicState(predicates().drop(state.size()))
            else null
        }
        else -> null
    }
}