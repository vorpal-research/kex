package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.driver.GenerationException
import org.jetbrains.research.kex.driver.RandomDriver
import org.jetbrains.research.kex.trace.Trace
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Method as KfgMethod

internal val runs = GlobalConfig.getIntValue("runner", "runs", 10)
internal val timeout = GlobalConfig.getLongValue("runner", "timeout", 1000L)

class RandomRunner(method: KfgMethod, loader: ClassLoader) : AbstractRunner(method, loader) {
    private val random = RandomDriver

    fun run() = repeat(runs) { _ ->
        if (TraceManager.isBodyCovered(method)) return
        val (instance, args) = try {
            val i = when {
                method.isStatic -> null
                else -> random.generate(javaClass)
            }
            val a = javaMethod.genericParameterTypes.map { random.generate(it) }.toTypedArray()
            i to a
        } catch (e: GenerationException) {
            log.debug("Cannot invoke $method")
            log.debug("Cause: ${e.message}")
            log.debug("Skipping method")
            return
        }

        val trace = try {
            invoke(instance, args)
        } catch (e: Exception) {
            log.error("Failed when running method $method")
            log.error("Exception: $e")
            Trace.parse(listOf(), e)
        }
        trace.forEach { TraceManager.addTrace(it.method, it) }
    }
}