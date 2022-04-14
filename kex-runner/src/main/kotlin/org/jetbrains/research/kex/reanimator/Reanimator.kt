package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.DescriptorRtMapper
import org.jetbrains.research.kex.ktype.KexRtManager
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.parameters.concreteParameters
import org.jetbrains.research.kex.parameters.filterStaticFinals
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequenceExecutor
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequenceRtMapper
import org.jetbrains.research.kex.reanimator.actionsequence.generator.ActionSequenceGenerator
import org.jetbrains.research.kex.reanimator.codegen.JUnitTestCasePrinter
import org.jetbrains.research.kex.reanimator.codegen.TestCasePrinter
import org.jetbrains.research.kex.reanimator.codegen.klassName
import org.jetbrains.research.kex.reanimator.codegen.packageName
import org.jetbrains.research.kex.reanimator.descriptor.DescriptorStatistics
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.system.measureTimeMillis

private val visibilityLevel by lazy {
    kexConfig.getEnumValue("testGen", "visibility", true, Visibility.PUBLIC)
}

val Parameters<Descriptor>.rtMapped: Parameters<Descriptor>
    get() {
        val mapper = DescriptorRtMapper(KexRtManager.Mode.MAP)
        val instance = instance?.let { mapper.map(it) }
        val args = arguments.map { mapper.map(it) }
        val statics = statics.map { mapper.map(it) }.toSet()
        return Parameters(instance, args, statics)
    }

val Parameters<ActionSequence>.rtUnmapped: Parameters<ActionSequence>
    get() {
        val mapper = ActionSequenceRtMapper(KexRtManager.Mode.UNMAP)
        val instance = instance?.let { mapper.map(it) }
        val args = arguments.map { mapper.map(it) }
        val statics = statics.map { mapper.map(it) }.toSet()
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
    private val csGenerator = ActionSequenceGenerator(ctx, psa, visibilityLevel)
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
            .concreteParameters(cm).rtMapped
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
            val staticFields = statics.map { it.actionSequence }.toSet()
            return Parameters(thisSequence, argSequences, staticFields)
        }

    val Parameters<ActionSequence>.executed: Parameters<Any?>
        get() {
            val instance = instance?.let { csExecutor.execute(it) }
            val args = arguments.map { csExecutor.execute(it) }
            val statics = statics.map { csExecutor.execute(it) }.toSet()
            return Parameters(instance, args, statics)
        }
}
