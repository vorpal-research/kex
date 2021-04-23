package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateInputByModel
import org.jetbrains.research.kfg.ir.Method

class ReflectionReanimator(override val ctx: ExecutionContext, override val psa: PredicateStateAnalysis) : ParameterGenerator {
    // todo: maybe add proper test generation

    override fun generate(testName: String, method: Method, state: PredicateState, model: SMTModel): Parameters<Any?> = try {
        generateInputByModel(ctx, method, state, model)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    }

    override fun emit() {}
}