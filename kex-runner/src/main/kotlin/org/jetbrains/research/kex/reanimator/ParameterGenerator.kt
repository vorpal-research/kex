package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kfg.ir.Method

interface ParameterGenerator {
    val ctx: ExecutionContext
    val psa: PredicateStateAnalysis

    fun generate(testName: String, method: Method, state: PredicateState, model: SMTModel): Parameters<Any?>
    fun emit()
}
