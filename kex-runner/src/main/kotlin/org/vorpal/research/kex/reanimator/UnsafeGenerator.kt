package org.vorpal.research.kex.reanimator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asserter.ExecutionExceptionFinalInfo
import org.vorpal.research.kex.asserter.ExecutionFinalInfo
import org.vorpal.research.kex.asserter.ExecutionSuccessFinalInfo
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.concreteParameters
import org.vorpal.research.kex.random.GenerationException
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.generator.ConcolicSequenceGenerator
import org.vorpal.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.vorpal.research.kex.reanimator.codegen.packageName
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.transformer.generateFinalDescriptors
import org.vorpal.research.kex.state.transformer.generateInputByModel
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path

class UnsafeGenerator(
    override val ctx: ExecutionContext,
    val method: Method,
    val testName: String
) : ParameterGenerator {
    private val asGenerator = ConcolicSequenceGenerator(ctx, PredicateStateAnalysis(ctx.cm))
    private val printer = ExecutorTestCasePrinter(ctx, method.packageName, testName)
    val testKlassName = printer.fullKlassName

    fun generate(descriptors: Parameters<Descriptor>, finalInfoDescriptors: ExecutionFinalInfo<Descriptor>? = null) = try {
        val sequences = descriptors.actionSequences
        val finalInfoSequences = finalInfoDescriptors?.actionSequences
        printer.print(method, sequences.rtUnmapped, finalInfoSequences)
    } catch (e: GenerationException) {
        log.warn("Generation error when generating action sequences:", e)
        throw e
    } catch (e: Exception) {
        log.warn("Exception when generating action sequences:", e)
        throw GenerationException(e)
    } catch (e: Error) {
        log.warn("Error when generating action sequences:", e)
        throw GenerationException(e)
    }

    fun generate(state: PredicateState, model: SMTModel) {
        val descriptors =
            generateFinalDescriptors(method, ctx, model, state).concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
        log.debug("Generated descriptors:\n{}", descriptors)
        generate(descriptors)
    }

    override fun generate(
        testName: String,
        method: Method,
        state: PredicateState,
        model: SMTModel
    ): Parameters<Any?> = try {
        val descriptors =
            generateFinalDescriptors(method, ctx, model, state).concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
        log.debug("Generated descriptors:\n{}", descriptors)
        val sequences = descriptors.actionSequences
        printer.print(testName, method, sequences.rtUnmapped)
        generateInputByModel(ctx, method, state, model)
    } catch (e: GenerationException) {
        log.warn("Generation error when generating action sequences:", e)
        throw e
    } catch (e: Exception) {
        log.warn("Exception when generating action sequences:", e)
        throw GenerationException(e)
    } catch (e: Error) {
        log.warn("Error when generating action sequences:", e)
        throw GenerationException(e)
    }


    override fun emit(): Path {
        printer.emit()
        return printer.targetFile.toPath()
    }

    val Descriptor.actionSequence: ActionSequence
        get() = asGenerator.generate(this)

    private val Parameters<Descriptor>.actionSequences: Parameters<ActionSequence>
        get() {
            asGenerator.initializeStaticFinals(statics)
            val thisSequence = instance?.actionSequence
            val argSequences = arguments.map { it.actionSequence }
            val staticFields = statics.mapTo(mutableSetOf()) { it.actionSequence }
            return Parameters(thisSequence, argSequences, staticFields)
        }

    private val ExecutionFinalInfo<Descriptor>.actionSequences : ExecutionFinalInfo<ActionSequence>
        get()  {
            val instance = this.instance?.let { asGenerator.generate(it) }
            val args = this.args.map { asGenerator.generate(it) }
            return when(this) {
                is ExecutionExceptionFinalInfo ->
                    ExecutionExceptionFinalInfo(instance, args, this.javaClass)
                is ExecutionSuccessFinalInfo ->
                    ExecutionSuccessFinalInfo(instance, args, this.retValue?.let { asGenerator.generate(it) })
            }
        }

}

