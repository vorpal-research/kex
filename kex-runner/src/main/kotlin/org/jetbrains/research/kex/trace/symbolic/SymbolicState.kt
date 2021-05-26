package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

data class Clause(val instruction: Instruction, val predicate: Predicate)

interface PathCondition {

    val path: List<Clause>
}

interface SymbolicState {
    val state: PredicateState
    val path: PathCondition
    val concreteValueMap: Map<Term, Descriptor>
    val termMap: Map<Term, Value>
    val predicateMap: Map<Predicate, Instruction>
}