package org.jetbrains.research.kex.asm.analysis.testgen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.Trace
import org.jetbrains.research.kex.trace.runner.ReanimatingRandomObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.logging.log

private val runs: Int by lazy { kexConfig.getIntValue("random-runner", "attempts", 10) }
private val runner: Boolean by lazy { kexConfig.getBooleanValue("random-runner", "enabled", false) }

class RandomChecker(
    val ctx: ExecutionContext,
    val psa: PredicateStateAnalysis,
    val visibilityLevel: Visibility,
    val tm: TraceManager<Trace>) : MethodVisitor {
    override val cm: ClassManager
        get() = ctx.cm
    override fun cleanup() {}

    override fun visit(method: Method) {
        super.visit(method)
        if (!runner) return
        if (method.`class`.isSynthetic) return
        if (method.isAbstract || method.isConstructor || method.isStaticInitializer) return

        val randomRunner = ReanimatingRandomObjectTracingRunner(ctx, psa, visibilityLevel, method)

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
