package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kfg.ir.Method
import java.nio.file.Path

interface ParameterGenerator {
    val ctx: ExecutionContext

    fun generate(testName: String, method: Method, state: PredicateState, model: SMTModel): Parameters<Any?>
    fun emit(): Path
}
