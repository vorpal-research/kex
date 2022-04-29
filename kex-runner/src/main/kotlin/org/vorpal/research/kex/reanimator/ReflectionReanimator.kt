package org.vorpal.research.kex.reanimator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.random.GenerationException
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.transformer.generateInputByModel
import org.vorpal.research.kfg.ir.Method
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