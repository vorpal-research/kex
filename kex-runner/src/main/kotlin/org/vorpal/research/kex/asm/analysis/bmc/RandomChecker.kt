package org.vorpal.research.kex.asm.analysis.bmc

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.TraceManager
import org.vorpal.research.kex.trace.`object`.ActionTrace
import org.vorpal.research.kex.trace.runner.RandomSymbolicTracingRunner
import org.vorpal.research.kex.trace.runner.ReanimatingRandomObjectTracingRunner
import org.vorpal.research.kex.util.TimeoutException
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log

private val runs: Int by lazy {
    kexConfig.getIntValue("random-runner", "attempts", 10)
}
private val runner: Boolean by lazy {
    kexConfig.getBooleanValue("random-runner", "enabled", false)
}

@Suppress("unused")
class RandomChecker(
    val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis,
    private val tm: TraceManager<ActionTrace>
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

        val randomRunner = ReanimatingRandomObjectTracingRunner(ctx, nameContext, psa, method)

        repeat(runs) {
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

@Suppress("unused")
class SymbolicRandomChecker(
    val ctx: ExecutionContext,
    val loader: ClassLoader,
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
                log.debug("Running method {}", method)
                val trace = randomRunner.run() ?: return@repeat
                log.debug(trace)
            } catch (e: TimeoutException) {
                log.warn("Method $method failed with timeout, skipping it")
                return
            }
        }
    }
}
