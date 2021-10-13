package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateInputByModel
import org.jetbrains.research.kfg.ir.Method
import java.nio.file.Path
import java.nio.file.Paths

@Deprecated("use ExecutionGenerator instead")
class ReflectionReanimator(
    override val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis
) : ParameterGenerator {
    // todo: maybe add proper test generation

    override fun generate(testName: String, method: Method, state: PredicateState, model: SMTModel): Parameters<Any?> = try {
        generateInputByModel(ctx, method, state, model)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    }

    override fun emit(): Path {
        return Paths.get(".")
    }
}