package org.jetbrains.research.kex.trace.symbolic

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.trace.AbstractTrace
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

@Serializable
data class InstructionTrace(
    val trace: List<@Contextual Instruction> = listOf()
) : AbstractTrace(), Iterable<Instruction> by trace

@Serializable
data class Clause(
    @Contextual val instruction: Instruction,
    val predicate: Predicate
)

@Serializable
abstract class PathCondition : Iterable<Clause> {
    abstract val path: List<Clause>
    override fun iterator() = path.iterator()
}

class ConcreteTermMap(private val map: Map<Term, @Contextual Descriptor>) : Map<Term, Descriptor> by map {
    constructor() : this(mapOf())
}

@Serializable
data class WrappedValue(val method: @Contextual Method, val value: @Contextual Value)

class ValueTermMap(private val map: Map<Term, @Contextual WrappedValue>) : Map<Term, WrappedValue> by map {
    constructor() : this(mapOf())
}

class ValuePredicateMap(private val map: Map<Predicate, @Contextual Instruction>) : Map<Predicate, Instruction> by map {
    constructor() : this(mapOf())
}

@Serializable
abstract class SymbolicState {
    abstract val state: PredicateState
    abstract val path: PathCondition
    abstract val concreteValueMap: ConcreteTermMap
    abstract val termMap: ValueTermMap
    abstract val predicateMap: ValuePredicateMap
    abstract val trace: InstructionTrace

    operator fun get(term: Term) = termMap.getValue(term)
    operator fun get(predicate: Predicate) = predicateMap.getValue(predicate)

    fun isEmpty() = state.isEmpty
    fun isNotEmpty() = state.isNotEmpty
}

@Serializable
@SerialName("SymbolicStateImpl")
data class SymbolicStateImpl(
    override val state: PredicateState,
    override val path: PathCondition,
    override val concreteValueMap: @Contextual ConcreteTermMap,
    override val termMap: @Contextual ValueTermMap,
    override val predicateMap: @Contextual ValuePredicateMap,
    override val trace: InstructionTrace
) : SymbolicState() {
    override fun toString() = "$state"
}

@Serializable
@SerialName("PathConditionImpl")
class PathConditionImpl(override val path: List<Clause>) : PathCondition()