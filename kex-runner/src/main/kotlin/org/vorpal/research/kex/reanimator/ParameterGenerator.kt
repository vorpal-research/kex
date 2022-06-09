package org.vorpal.research.kex.reanimator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kfg.ir.Method
import java.nio.file.Path

interface ParameterGenerator {
    val ctx: ExecutionContext

    fun generate(testName: String, method: Method, state: PredicateState, model: SMTModel): Parameters<Any?>
    fun emit(): Path
}
