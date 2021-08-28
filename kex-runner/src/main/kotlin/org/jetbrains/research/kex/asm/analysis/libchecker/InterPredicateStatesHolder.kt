package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.TermRenamer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.CallInst

class InterPredicateStatesHolder {
    val map = mutableMapOf<Method, Set<PredicateStateWithInfo>>()
}

data class PredicateStateWithInfo(
    var predicateState: PredicateState,
    val callStack: List<CallInst>,
    val renamer: TermRenamer?,
    val asserts: Set<Term>
)