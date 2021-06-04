package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

data class InstructionTrace(val trace: List<Instruction> = listOf()) : Iterable<Instruction> by trace

data class Clause(val instruction: Instruction, val predicate: Predicate)

interface PathCondition : Iterable<Clause> {
    val path: List<Clause>

    override fun iterator() = path.iterator()
}

interface SymbolicState {
    val state: PredicateState
    val path: PathCondition
    val concreteValueMap: Map<Term, Descriptor>
    val termMap: Map<Term, Value>
    val predicateMap: Map<Predicate, Instruction>
    val trace: InstructionTrace

    operator fun get(term: Term) = termMap.getValue(term)
    operator fun get(predicate: Predicate) = predicateMap.getValue(predicate)

    fun isEmpty() = state.isEmpty
    fun isNotEmpty() = state.isNotEmpty
}

internal data class SymbolicStateImpl(
    override val state: PredicateState,
    override val path: PathCondition,
    override val concreteValueMap: Map<Term, Descriptor>,
    override val termMap: Map<Term, Value>,
    override val predicateMap: Map<Predicate, Instruction>,
    override val trace: InstructionTrace
) : SymbolicState {
    override fun toString() = "$state"
}

internal data class PathConditionImpl(override val path: List<Clause>) : PathCondition