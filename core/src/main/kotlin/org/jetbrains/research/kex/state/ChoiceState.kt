package org.jetbrains.research.kex.state

import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.util.defaultHashCode

@InheritorOf("State")
class ChoiceState(val choices: List<PredicateState>) : PredicateState(), Iterable<PredicateState> {
    override val size: Int
        get() = choices.fold(0) { acc, it -> acc + it.size }

    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("(BEGIN")
        sb.append(choices.joinToString { " <OR> {$it}" })
        sb.append(" END)")
        return sb.toString()
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
        val slices = choices.map { it.sliceOn(state) }
        val filtered = slices.filterNotNull()
        return if (slices.size == filtered.size) ChoiceState(filtered) else null
    }

    override fun iterator() = choices.iterator()

    override fun simplify(): PredicateState {
        val simplifiedChoices = choices.map { it.simplify() }.filter { it.isNotEmpty }
        return when {
            simplifiedChoices.isEmpty() -> StateBuilder().apply()
            simplifiedChoices.size == 1 -> simplifiedChoices.first()
            simplifiedChoices == choices -> this
            else -> (StateBuilder() + simplifiedChoices).apply()
        }
    }
}