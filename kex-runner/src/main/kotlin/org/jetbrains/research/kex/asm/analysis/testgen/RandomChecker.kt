package org.jetbrains.research.kex.asm.analysis.testgen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.ActionTrace
import org.jetbrains.research.kex.trace.runner.RandomSymbolicTracingRunner
import org.jetbrains.research.kex.trace.runner.ReanimatingRandomObjectTracingRunner
import org.jetbrains.research.kex.trace.symbolic.InstructionTrace
import org.jetbrains.research.kex.util.TimeoutException
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.NameMapperContext
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log

private val runs: Int by lazy {
    kexConfig.getIntValue("random-runner", "attempts", 10)
}
private val runner: Boolean by lazy {
    kexConfig.getBooleanValue("random-runner", "enabled", false)
}

class RandomChecker(
    val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis,
    val visibilityLevel: Visibility,
    val tm: TraceManager<ActionTrace>
) : MethodVisitor {
    override val cm: ClassManager
        get() = ctx.cm
    private val nameContext = NameMapperContext()

    override fun cleanup() {
        nameContext.clear()
    }

    override fun visit(method: Method) {
        super.visit(method)
        if (!runner) return
        if (method.klass.isSynthetic) return
        if (method.isAbstract || method.isConstructor || method.isStaticInitializer) return

        val randomRunner = ReanimatingRandomObjectTracingRunner(ctx, nameContext, psa, visibilityLevel, method)

        repeat(runs) { _ ->
            try {
                val trace = randomRunner.run() ?: return@repeat
                tm[method] = trace
            } catch (e: TimeoutException) {
                log.warn("Method $method failed with timeout, skipping it")
                return
            }
        }

        randomRunner.emit()
    }
}

class SymbolicRandomChecker(
    val ctx: ExecutionContext,
    val loader: ClassLoader,
    val tm: TraceManager<InstructionTrace>
) : MethodVisitor {
    override val cm: ClassManager
        get() = ctx.cm
    private val nameContext = NameMapperContext()

    override fun cleanup() {
        nameContext.clear()
    }

    override fun visit(method: Method) {
        super.visit(method)
        if (!runner) return
        if (method.klass.isSynthetic) return
        if (method.isAbstract || method.isConstructor || method.isStaticInitializer) return

        val randomRunner = RandomSymbolicTracingRunner(ctx, nameContext, method)

        repeat(runs) { _ ->
            try {
                log.debug("Running method $method")
                val trace = randomRunner.run() ?: return@repeat
                tm[method] = trace.trace
                log.debug(trace)
            } catch (e: TimeoutException) {
                log.warn("Method $method failed with timeout, skipping it")
                return
            }
        }
    }
}