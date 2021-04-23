package org.jetbrains.research.kex.state

import org.jetbrains.research.kthelper.defaultHashCode
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.state.predicate.Predicate

@InheritorOf("State")
@Serializable
class ChoiceState(val choices: List<PredicateState>) : PredicateState(), Iterable<PredicateState> {
    override val size: Int
        get() = choices.fold(0) { acc, it -> acc + it.size }

    override fun print() = buildString {
        appendLine("(BEGIN")
        append(choices.joinToString { " <OR> $it" })
        append(" END)")
    }

    override fun fmap(transform: (PredicateState) -> PredicateState): PredicateState = ChoiceState(choices.map { transform(it) })
    override fun reverse() = ChoiceState(choices.map { it.reverse() })

    override fun hashCode() = defaultHashCode(*choices.toTypedArray())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as ChoiceState
        return this.choices.toTypedArray().contentEquals(other.choices.toTypedArray())
    }

    override fun addPredicate(predicate: Predicate) = ChainState(ChoiceState(choices), BasicState(arrayListOf(predicate)))

    override fun sliceOn(state: PredicateState): PredicateState? {
        if (this == state) return emptyState()
        val slices = choices.map { it.sliceOn(state) }
        val filtered = slices.filterNotNull()
        return if (slices.size == filtered.size) ChoiceState(filtered) else null
    }

    override fun iterator() = choices.iterator()

    override fun simplify(): PredicateState {
        val simplifiedChoices = choices.map { it.simplify() }.filter { it.isNotEmpty }
        return when {
            simplifiedChoices.isEmpty() -> emptyState()
            simplifiedChoices.size == 1 -> simplifiedChoices.first()
            simplifiedChoices == choices -> this
            else -> ChoiceState(simplifiedChoices)
        }
    }
}