package org.vorpal.research.kex.reanimator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.DescriptorRtMapper
import org.vorpal.research.kex.ktype.KexRtManager
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.concreteParameters
import org.vorpal.research.kex.parameters.filterStaticFinals
import org.vorpal.research.kex.random.GenerationException
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequenceExecutor
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequenceRtMapper
import org.vorpal.research.kex.reanimator.actionsequence.generator.ActionSequenceGenerator
import org.vorpal.research.kex.reanimator.codegen.JUnitTestCasePrinter
import org.vorpal.research.kex.reanimator.codegen.TestCasePrinter
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.reanimator.codegen.packageName
import org.vorpal.research.kex.reanimator.descriptor.DescriptorStatistics
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.transformer.generateFinalDescriptors
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import java.nio.file.Path
import kotlin.system.measureTimeMillis

val Parameters<Descriptor>.rtMapped: Parameters<Descriptor>
    get() {
        val mapper = DescriptorRtMapper(KexRtManager.Mode.MAP)
        val instance = instance?.let { mapper.map(it) }
        val args = arguments.map { mapper.map(it) }
        val statics = statics.mapTo(mutableSetOf()) { mapper.map(it) }
        return Parameters(instance, args, statics)
    }

val Parameters<ActionSequence>.rtUnmapped: Parameters<ActionSequence>
    get() {
        val mapper = ActionSequenceRtMapper(KexRtManager.Mode.UNMAP)
        val instance = instance?.let { mapper.map(it) }
        val args = arguments.map { mapper.map(it) }
        val statics = statics.mapTo(mutableSetOf()) { mapper.map(it) }
        return Parameters(instance, args, statics)
    }

class Reanimator(
    override val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis,
    packageName: String,
    klassName: String
) : ParameterGenerator {
    val cm: ClassManager get() = ctx.cm
    val printer: TestCasePrinter = JUnitTestCasePrinter(ctx, packageName, klassName)
    private val csGenerator = ActionSequenceGenerator(ctx, psa)
    private val csExecutor = ActionSequenceExecutor(ctx)

    constructor(ctx: ExecutionContext, psa: PredicateStateAnalysis, method: Method) : this(
        ctx,
        psa,
        method.packageName,
        method.klassName
    )

    override fun generate(testName: String, method: Method, state: PredicateState, model: SMTModel) = try {
        val descriptors = generateFinalDescriptors(method, ctx, model, state)
            .filterStaticFinals(cm)
            .concreteParameters(cm, ctx.accessLevel, ctx.random).rtMapped
        log.debug("Generated descriptors:\n$descriptors")
        val actionSequences = descriptors.actionSequences
        log.debug("Generated action sequences:\n$actionSequences")
        val unmapped = actionSequences.rtUnmapped
        log.debug("Unmapped action sequences:\n$unmapped")
        printer.print(testName, method, unmapped)
        unmapped.executed
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
        get() = `try` {
            lateinit var cs: ActionSequence
            val time = measureTimeMillis { cs = csGenerator.generateDescriptor(this) }
            DescriptorStatistics.addDescriptor(this, cs, time)
            cs
        }.getOrThrow {
            DescriptorStatistics.addFailure(this@actionSequence)
            this
        }

    val Parameters<Descriptor>.actionSequences: Parameters<ActionSequence>
        get() {
            val thisSequence = instance?.actionSequence
            val argSequences = arguments.map { it.actionSequence }
            val staticFields = statics.mapTo(mutableSetOf()) { it.actionSequence }
            return Parameters(thisSequence, argSequences, staticFields)
        }

    val Parameters<ActionSequence>.executed: Parameters<Any?>
        get() {
            val instance = instance?.let { csExecutor.execute(it) }
            val args = arguments.map { csExecutor.execute(it) }
            val statics = statics.mapTo(mutableSetOf()) { csExecutor.execute(it) }
            return Parameters(instance, args, statics)
        }
}
