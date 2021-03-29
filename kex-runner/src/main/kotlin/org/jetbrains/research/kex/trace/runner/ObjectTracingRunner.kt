package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.reanimator.Reanimator
import org.jetbrains.research.kex.reanimator.callstack.generator.GeneratorContext
import org.jetbrains.research.kex.reanimator.codegen.klassName
import org.jetbrains.research.kex.reanimator.codegen.packageName
import org.jetbrains.research.kex.reanimator.descriptor.descriptor
import org.jetbrains.research.kex.trace.`object`.Trace
import org.jetbrains.research.kex.trace.`object`.TraceCollectorProxy
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull

class ObjectTracingRunner(method: Method, loader: ClassLoader) : TracingAbstractRunner<Trace>(method, loader) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val collector = TraceCollectorProxy.enableCollector(method.cm)
        run(instance, args)
        TraceCollectorProxy.disableCollector()
        return Trace(collector.trace)
    }
}

class RandomObjectTracingRunner(method: Method, loader: ClassLoader, random: Randomizer) :
    TracingRandomRunner<Trace>(method, loader, random) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val collector = TraceCollectorProxy.enableCollector(method.cm)
        run(instance, args)
        TraceCollectorProxy.disableCollector()
        return Trace(collector.trace)
    }
}

class ReanimatingRandomObjectTracingRunner(
    val ctx: ExecutionContext,
    psa: PredicateStateAnalysis,
    visibilityLevel: Visibility,
    method: Method
) : TracingRandomRunner<Trace>(method, ctx.loader, ctx.random) {
    val generatorContext = GeneratorContext(ctx, psa, visibilityLevel)
    val reanimator = Reanimator(ctx, psa, method.packageName, "Random${method.klassName}")
    private var testCounter = 0

    val Parameters<Any?>.descriptors get() = Parameters(instance.descriptor, arguments.map { it.descriptor }, setOf())

    override fun run(): Trace? = tryOrNull {
        val (randomInstance, randomArgs) = when {
            method.isConstructor -> null to random.generate(javaConstructor)
            else -> random.generate(javaClass, javaMethod)
        }
        if (randomArgs == null || (!method.isStatic && !method.isConstructor && randomInstance == null)) {
            log.error("Cannot generate parameters to invoke method $method")
            return null
        }
        val parameters = Parameters(randomInstance, randomArgs.toList(), setOf())
        val (instance, args) = with (reanimator) {
            val descriptors = parameters.descriptors
            val callStacks = descriptors.callStacks
            printTest("test_$testCounter", method, callStacks)
            callStacks.executed
        }
        return collectTrace(instance, args.toTypedArray())
    }

    override fun collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val collector = TraceCollectorProxy.enableCollector(method.cm)
        run(instance, args)
        TraceCollectorProxy.disableCollector()
        return Trace(collector.trace)
    }

    fun emit() {
        reanimator.emit()
    }
}