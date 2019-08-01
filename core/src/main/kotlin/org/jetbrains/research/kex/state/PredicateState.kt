package org.jetbrains.research.kex.state

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateBuilder
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.util.fail
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Location

interface TypeInfo {
    val inheritors: Map<String, Class<*>>
    val reverseMapping: Map<Class<*>, String>
}

fun emptyState(): PredicateState = BasicState()
fun Predicate.wrap() = emptyState() + this

class StateBuilder() : PredicateBuilder() {
    override val type = PredicateType.State()
    override val location = Location()
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

    inline fun assume(body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.Assume().body()
    }
    inline fun assume(location: Location, body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.Assume(location).body()
    }

    inline fun state(body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.State().body()
    }
    inline fun state(location: Location, body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.State(location).body()
    }

    inline fun path(body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.Path().body()
    }
    inline fun path(location: Location, body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.Path(location).body()
    }

    inline fun require(body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.Require().body()
    }
    inline fun require(location: Location, body: PredicateBuilder.() -> Predicate) {
        this += PredicateBuilder.Require(location).body()
    }

    inline fun predicate(type: PredicateType, body: PredicateBuilder.() -> Predicate) = when (type) {
        is PredicateType.Assume -> assume(body)
        is PredicateType.Require -> require(body)
        is PredicateType.State -> state(body)
        is PredicateType.Path -> path(body)
        else -> fail { log.error("Unknown predicate type $type") }
    }

    inline fun predicate(type: PredicateType, location: Location, body: PredicateBuilder.() -> Predicate) = when (type) {
        is PredicateType.Assume -> assume(location, body)
        is PredicateType.Require -> require(location, body)
        is PredicateType.State -> state(location, body)
        is PredicateType.Path -> path(location, body)
        else -> fail { log.error("Unknown predicate type $type") }
    }
}

inline fun basic(body: StateBuilder.() -> Unit): PredicateState {
    val sb = StateBuilder()
    sb.body()
    return sb.apply()
}

inline fun chain(base: StateBuilder.() -> Unit, curr: StateBuilder.() -> Unit): PredicateState {
    val sb = StateBuilder().apply { base() }
    sb += StateBuilder().apply { curr() }.apply()
    return sb.apply()
}

inline fun choice(left: StateBuilder.() -> Unit, right: StateBuilder.() -> Unit): PredicateState {
    val lhv = StateBuilder().apply { left() }.apply()
    val rhv = StateBuilder().apply { right() }.apply()
    return StateBuilder().apply { this += listOf(lhv, rhv) }.apply()
}

@BaseType("State")
@Serializable
abstract class PredicateState : TypeInfo {
    companion object {
        val states = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("PredicateState.json")
                    ?: unreachable { log.error("No info about PS inheritors") }
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
    fun all(predicate: (Predicate) -> Boolean): Boolean = size == filter(predicate).size
    fun any(predicate: (Predicate) -> Boolean): Boolean = filter(predicate).size > 0
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