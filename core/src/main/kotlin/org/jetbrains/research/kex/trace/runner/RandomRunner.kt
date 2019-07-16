package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Method as KfgMethod

internal val runs = kexConfig.getIntValue("runner", "runs", 10)

class RandomRunner(method: KfgMethod, loader: ClassLoader) : AbstractRunner(method, loader) {
    private val random = defaultRandomizer

    fun run() = repeat(runs) { _ ->
        if (TraceManager.isBodyCovered(method)) return
        val (instance, args) = try {
            val i = when {
                method.isStatic -> null
                else -> random.next(javaClass)
            }
            val a = javaMethod.genericParameterTypes.map { random.next(it) }.toTypedArray()
            i to a
        } catch (e: GenerationException) {
            log.debug("Cannot invoke $method")
            log.debug("Cause: ${e.message}")
            log.debug("Skipping method")
            return
        }

        val trace = try {
            invoke(instance, args)
        } catch (e: TimeoutException) {
            log.error("Failed method $method with timeout, skipping it")
            return
        } catch (e: Exception) {
            log.error("Failed when running method $method")
            null
        } ?: return@repeat

        TraceManager.addTrace(method, trace)
    }
}