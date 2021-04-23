package org.jetbrains.research.kex.state

import org.jetbrains.research.kthelper.defaultHashCode
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.state.predicate.Predicate

@InheritorOf("State")
@Serializable
class BasicState(@Required val predicates: List<Predicate> = listOf()) : PredicateState(), Iterable<Predicate> {

    override val size: Int
        get() = predicates.size

    override fun print() = buildString {
        appendLine("(")
        predicates.forEach { appendLine("  $it") }
        append(")")
    }

    override fun map(transform: (Predicate) -> Predicate) = BasicState(predicates.map(transform))
    override fun fmap(transform: (PredicateState) -> PredicateState) = transform(this)
    override fun mapNotNull(transform: (Predicate) -> Predicate?) = BasicState(predicates.mapNotNull(transform))
    override fun filter(predicate: (Predicate) -> Boolean) = BasicState(predicates.filter(predicate))
    override fun reverse(): PredicateState = BasicState(predicates.reversed())

    override fun hashCode() = defaultHashCode(*predicates.toTypedArray())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as BasicState
        return this.predicates == other.predicates
    }

    override fun addPredicate(predicate: Predicate) = BasicState(predicates + predicate)

    override fun sliceOn(state: PredicateState): PredicateState? = when (state) {
        is BasicState -> when (predicates.take(state.size)) {
            state.predicates -> BasicState(predicates.drop(state.size))
            else -> null
        }
        else -> null
    }

    override fun iterator() = predicates.iterator()

    override fun simplify(): PredicateState = this
}