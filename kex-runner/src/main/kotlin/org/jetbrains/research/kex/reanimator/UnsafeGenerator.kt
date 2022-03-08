package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.parameters.concreteParameters
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.generator.ConcolicSequenceGenerator
import org.jetbrains.research.kex.reanimator.actionsequence.generator.UnknownGenerator
import org.jetbrains.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.jetbrains.research.kex.reanimator.codegen.packageName
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kex.state.transformer.generateInputByModel
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.logging.log
import java.nio.file.Path

private val visibilityLevel by lazy {
    kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)
}

class UnsafeGenerator(
    override val ctx: ExecutionContext,
    val method: Method,
    val testName: String
) : ParameterGenerator {
    private val asGenerator = ConcolicSequenceGenerator(ctx, PredicateStateAnalysis(ctx.cm), visibilityLevel)
    private val unknownGenerator = UnknownGenerator(asGenerator.context)
    private val printer = ExecutorTestCasePrinter(ctx, method.packageName, testName)
    val testKlassName = printer.fullKlassName

    fun generate(descriptors: Parameters<Descriptor>) = try {
        val sequences = descriptors.actionSequences
        printer.print(method, sequences.rtUnmapped)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    } catch (e: Error) {
        throw GenerationException(e)
    }

    fun generateUnsafe(descriptors: Parameters<Descriptor>) = try {
        val sequences = descriptors.unknownActionSequences
        printer.print(method, sequences.rtUnmapped)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    } catch (e: Error) {
        throw GenerationException(e)
    }

    fun generate(state: PredicateState, model: SMTModel) {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concreteParameters(ctx.cm)
        log.debug("Generated descriptors:\n$descriptors")
        generate(descriptors)
    }

    override fun generate(
        testName: String,
        method: Method,
        state: PredicateState,
        model: SMTModel
    ): Parameters<Any?> = try {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concreteParameters(ctx.cm)
        log.debug("Generated descriptors:\n$descriptors")
        val sequences = descriptors.actionSequences
        printer.print(testName, method, sequences.rtUnmapped)
        generateInputByModel(ctx, method, state, model)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    } catch (e: Error) {
        throw GenerationException(e)
    }


    override fun emit(): Path {
        printer.emit()
        return printer.targetFile.toPath()
    }

    val Descriptor.actionSequence: ActionSequence
        get() = asGenerator.generate(this)

    val Descriptor.unknownActionSequence: ActionSequence
        get() = unknownGenerator.generate(this)

    private val Parameters<Descriptor>.actionSequences: Parameters<ActionSequence>
        get() {
            val thisSequence = instance?.actionSequence
            val argSequences = arguments.map { it.actionSequence }
            val staticFields = statics.map { it.actionSequence }.toSet()
            return Parameters(thisSequence, argSequences, staticFields)
        }

    private val Parameters<Descriptor>.unknownActionSequences: Parameters<ActionSequence>
        get() {
            val thisSequence = instance?.unknownActionSequence
            val argSequences = arguments.map { it.unknownActionSequence }
            val staticFields = statics.map { it.unknownActionSequence }.toSet()
            return Parameters(thisSequence, argSequences, staticFields)
        }
}
