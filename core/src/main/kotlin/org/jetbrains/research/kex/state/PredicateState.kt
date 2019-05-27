package org.jetbrains.research.kex.state

import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType

interface TypeInfo {
    val inheritors: Map<String, Class<*>>
    val reverseMapping: Map<Class<*>, String>
}

fun emptyState(): PredicateState = BasicState()
fun Predicate.wrap() = emptyState() + this

class StateBuilder() {
    var current: PredicateState = emptyState()

    constructor(state: PredicateState) : this() {
        this.current = state
    }

    operator fun plus(predicate: Predicate): StateBuilder {
        return StateBuilder(current + predicate)
    }

    operator fun plusAssign(predicate: Predicate) {
        current += predicate
    }

    operator fun plus(state: PredicateState) = when {
        state.isEmpty -> this
        current.isEmpty -> StateBuilder(state)
        else -> StateBuilder(ChainState(current, state))
    }

    operator fun plusAssign(state: PredicateState) {
        current = ChainState(current, state)
    }

    operator fun plus(choices: List<PredicateState>) = when {
        choices.isEmpty() -> this
        current.isEmpty -> StateBuilder(ChoiceState(choices))
        else -> StateBuilder(ChainState(current, ChoiceState(choices)))
    }

    operator fun plusAssign(choices: List<PredicateState>) {
        val choice = ChoiceState(choices)
        current = ChainState(current, choice)
    }

    fun apply() = current
}

@BaseType("State")
abstract class PredicateState : TypeInfo {
    companion object {
        val states = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("PredicateState.json")
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo?.inheritors?.map {
                it.name to loader.loadClass(it.inheritorClass)
            }?.toMap() ?: mapOf()
        }

        val reverse = states.map { it.value to it.key }.toMap()
    }

    abstract val size: Int

    val isEmpty: Boolean
        get() = size == 0

    val isNotEmpty: Boolean
        get() = !isEmpty

    override val inheritors get() = states
    override val reverseMapping get() = reverse

    abstract fun print(): String

    override fun toString() = print()

    open fun map(transform: (Predicate) -> Predicate): PredicateState = fmap { it.map(transform) }
    open fun mapNotNull(transform: (Predicate) -> Predicate?): PredicateState = fmap { it.mapNotNull(transform) }
    open fun filter(predicate: (Predicate) -> Boolean): PredicateState = fmap { it.filter(predicate) }
    fun filterNot(predicate: (Predicate) -> Boolean) = filter { !predicate(it) }
    fun filterByType(type: PredicateType) = filter { it.type == type }

    abstract fun fmap(transform: (PredicateState) -> PredicateState): PredicateState
    abstract fun reverse(): PredicateState

    abstract fun addPredicate(predicate: Predicate): PredicateState
    operator fun plus(predicate: Predicate) = addPredicate(predicate)

    abstract fun sliceOn(state: PredicateState): PredicateState?

    abstract fun simplify(): PredicateState

    fun builder() = StateBuilder(this)
    operator fun plus(state: PredicateState) = (builder() + state).apply()
    operator fun plus(states: List<PredicateState>) = (builder() + states).apply()
}