package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.parameters.concreteParameters
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.CallStackExecutor
import org.jetbrains.research.kex.reanimator.callstack.CallStackRtUnmapper
import org.jetbrains.research.kex.reanimator.callstack.generator.CallStackGenerator
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
import kotlin.system.measureTimeMillis

private val visibilityLevel by lazy {
    kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)
}

val Parameters<CallStack>.rtUnmapped: Parameters<CallStack>
    get() {
        val unmapper = CallStackRtUnmapper()
        val instance = instance?.let { unmapper.unmap(it) }
        val args = arguments.map { unmapper.unmap(it) }
        val statics = statics.map { unmapper.unmap(it) }.toSet()
        return Parameters(instance, args, statics)
    }

class Reanimator(
    override val ctx: ExecutionContext,
    override val psa: PredicateStateAnalysis,
    packageName: String,
    klassName: String
) : ParameterGenerator {
    val cm: ClassManager get() = ctx.cm
    val printer: TestCasePrinter = JUnitTestCasePrinter(ctx, packageName, klassName)
    private val csGenerator = CallStackGenerator(ctx, psa, visibilityLevel)
    private val csExecutor = CallStackExecutor(ctx)

    constructor(ctx: ExecutionContext, psa: PredicateStateAnalysis, method: Method) : this(
        ctx,
        psa,
        method.packageName,
        method.klassName
    )

    override fun generate(testName: String, method: Method, state: PredicateState, model: SMTModel) = try {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concreteParameters(cm)
        log.debug("Generated descriptors:\n$descriptors")
        val callStacks = descriptors.callStacks
        log.debug("Generated call stacks:\n$callStacks")
        val unmapped = callStacks.rtUnmapped
        log.debug("Unmapped call stacks:\n$unmapped")
        printer.print(testName, method, unmapped)
        unmapped.executed
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    } catch (e: Error) {
        throw GenerationException(e)
    }

    override fun emit() {
        printer.emit()
    }

    val Descriptor.callStack: CallStack
        get() = `try` {
            lateinit var cs: CallStack
            val time = measureTimeMillis { cs = csGenerator.generateDescriptor(this) }
            DescriptorStatistics.addDescriptor(this, cs, time)
            cs
        }.getOrThrow {
            DescriptorStatistics.addFailure(this@callStack)
            this
        }

    val Parameters<Descriptor>.callStacks: Parameters<CallStack>
        get() {
            val thisCallStack = instance?.callStack
            val argCallStacks = arguments.map { it.callStack }
            val staticFields = statics.map { it.callStack }.toSet()
            return Parameters(thisCallStack, argCallStacks, staticFields)
        }

    val Parameters<CallStack>.executed: Parameters<Any?>
        get() {
            val instance = instance?.let { csExecutor.execute(it) }
            val args = arguments.map { csExecutor.execute(it) }
            val statics = statics.map { csExecutor.execute(it) }.toSet()
            return Parameters(instance, args, statics)
        }
}
