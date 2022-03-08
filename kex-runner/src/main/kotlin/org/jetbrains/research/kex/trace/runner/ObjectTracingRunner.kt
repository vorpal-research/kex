package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.descriptor.Object2DescriptorConverter
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.reanimator.Reanimator
import org.jetbrains.research.kex.reanimator.actionsequence.generator.GeneratorContext
import org.jetbrains.research.kex.reanimator.codegen.klassName
import org.jetbrains.research.kex.reanimator.codegen.packageName
import org.jetbrains.research.kex.trace.`object`.ActionTrace
import org.jetbrains.research.kex.trace.`object`.TraceCollector
import org.jetbrains.research.kex.trace.`object`.TraceCollectorProxy
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.NameMapperContext
import org.jetbrains.research.kthelper.logging.log

class ObjectTracingRunner(
    val nameContext: NameMapperContext,
    method: Method,
    loader: ClassLoader,
    val parameters: Parameters<Any?>
) : TracingAbstractRunner<ActionTrace>(method, loader) {
    private lateinit var collector: TraceCollector

    override fun generateArguments() = parameters

    override fun enableCollector() {
        collector = TraceCollectorProxy.enableCollector(method.cm, nameContext)
    }

    override fun disableCollector() {
        TraceCollectorProxy.disableCollector()
    }

    override fun collectTrace(invocationResult: InvocationResult) = ActionTrace(collector.trace)
}

class RandomObjectTracingRunner(
    val nameContext: NameMapperContext,
    method: Method,
    loader: ClassLoader,
    random: Randomizer
) : TracingRandomRunner<ActionTrace>(method, loader, random) {
    private lateinit var collector: TraceCollector

    override fun enableCollector() {
        collector = TraceCollectorProxy.enableCollector(method.cm, nameContext)
    }

    override fun disableCollector() {
        TraceCollectorProxy.disableCollector()
    }

    override fun collectTrace(invocationResult: InvocationResult) = ActionTrace(collector.trace)
}

class ReanimatingRandomObjectTracingRunner(
    val ctx: ExecutionContext,
    val nameContext: NameMapperContext,
    psa: PredicateStateAnalysis,
    visibilityLevel: Visibility,
    method: Method
) : TracingRandomRunner<ActionTrace>(method, ctx.loader, ctx.random) {
    val generatorContext = GeneratorContext(ctx, psa, visibilityLevel)
    val reanimator = Reanimator(ctx, psa, method.packageName, "Random${method.klassName}")
    private var testCounter = 0

    private lateinit var collector: TraceCollector

    override fun enableCollector() {
        collector = TraceCollectorProxy.enableCollector(method.cm, nameContext)
    }

    override fun disableCollector() {
        TraceCollectorProxy.disableCollector()
    }

    override fun collectTrace(invocationResult: InvocationResult) = ActionTrace(collector.trace)

    private val Parameters<Any?>.descriptors
        get() = with (Object2DescriptorConverter()) {
            Parameters(convert(instance), arguments.map { convert(it) }, statics.map { convert(it) }.toSet())
        }

    override fun generateArguments(): Parameters<Any?>? {
        val (randomInstance, randomArgs) = super.generateArguments() ?: return null
        if (!method.isStatic && !method.isConstructor && randomInstance == null) {
            log.error("Cannot generate parameters to invoke method $method")
            return null
        }
        val parameters = Parameters(randomInstance, randomArgs.toList(), setOf())
        val (instance, args) = with(reanimator) {
            val descriptors = parameters.descriptors
            val callStacks = descriptors.actionSequences
            printer.print("test_$testCounter", method, callStacks)
            callStacks.executed
        }
        return Parameters(instance, args, setOf())
    }

    fun emit() {
        reanimator.emit()
    }
}