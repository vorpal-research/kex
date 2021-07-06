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

@Serializable
data class WrappedValue(val method: @Contextual Method, val value: @Contextual Value)

@Serializable
abstract class SymbolicState {
    abstract val state: PredicateState
    abstract val path: PathCondition
    abstract val concreteValueMap: Map<Term, @Contextual Descriptor>
    abstract val termMap: Map<Term, @Contextual WrappedValue>
    abstract val predicateMap: Map<Predicate, @Contextual Instruction>
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
    override val concreteValueMap: @Contextual Map<Term, @Contextual Descriptor>,
    override val termMap: @Contextual Map<Term, @Contextual WrappedValue>,
    override val predicateMap: @Contextual Map<Predicate, @Contextual Instruction>,
    override val trace: InstructionTrace
) : SymbolicState() {
    override fun toString() = "$state"
}

@Serializable
@SerialName("PathConditionImpl")
data class PathConditionImpl(override val path: List<Clause>) : PathCondition()