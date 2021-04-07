package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.CallStackExecutor
import org.jetbrains.research.kex.reanimator.callstack.MethodCall
import org.jetbrains.research.kex.reanimator.callstack.StaticMethodCall
import org.jetbrains.research.kex.reanimator.callstack.generator.CallStackGenerator
import org.jetbrains.research.kex.reanimator.codegen.TestCasePrinter
import org.jetbrains.research.kex.reanimator.codegen.klassName
import org.jetbrains.research.kex.reanimator.codegen.packageName
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.DescriptorStatistics
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.time.timed

class NoConcreteInstanceException(val klass: Class) : Exception()

private val visibilityLevel by lazy {
    kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)
}

class Reanimator(
    override val ctx: ExecutionContext,
    override val psa: PredicateStateAnalysis,
    packageName: String,
    klassName: String
) : ParameterGenerator {
    val cm: ClassManager get() = ctx.cm
    val printer = TestCasePrinter(ctx, packageName, klassName)
    private val csGenerator = CallStackGenerator(ctx, psa, visibilityLevel)
    private val csExecutor = CallStackExecutor(ctx)
    private var staticCounter = 0

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
        printTest(testName, method, callStacks)
        log.debug("Generated call stacks:\n$callStacks")
        callStacks.executed
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

    fun printTest(testName: String, method: Method, callStacks: Parameters<CallStack>) {
        val stack = when {
            method.isStatic -> StaticMethodCall(method, callStacks.arguments).wrap("static${staticCounter++}")
            method.isConstructor -> callStacks.instance!!
            else -> {
                val instance = callStacks.instance!!.clone()
                instance.stack += MethodCall(method, callStacks.arguments)
                instance
            }
        }
        printer.print(stack, testName)
    }

    val Descriptor.callStack: CallStack
        get() = `try` {
            lateinit var cs: CallStack
            val time = timed { cs = csGenerator.generateDescriptor(this) }
            DescriptorStatistics.addDescriptor(this, cs, time)
            cs
        }.getOrThrow {
            DescriptorStatistics.addFailure(this)
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
