package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.ConstantPropagator
import org.jetbrains.research.kex.state.transformer.MemorySpacer
import org.jetbrains.research.kex.state.transformer.StateOptimizer
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

class Checker(val method: Method, val psa: PredicateStateAnalysis) : Loggable {
    private fun prepareState(state: PredicateState): PredicateState {
        val optimized = StateOptimizer().transform(state)

        val propagated = ConstantPropagator().transform(optimized)
        val memspaced = MemorySpacer(propagated).transform(propagated)
        return memspaced
    }

    fun checkReachable(inst: Instruction): Result {
        log.debug("Checking reachability of ${inst.print()}")

        val state = psa.getInstructionState(inst)
        val finalState = prepareState(state)

        log.debug("State: $finalState")

        val result = SMTProxySolver().isReachable(finalState)
        log.debug("Acquired result:")
        when (result) {
            is Result.SatResult -> log.debug("Reachable")
            is Result.UnsatResult -> log.debug("Unreachable")
            is Result.UnknownResult -> log.debug("Unknown")
        }
        return result
    }
}