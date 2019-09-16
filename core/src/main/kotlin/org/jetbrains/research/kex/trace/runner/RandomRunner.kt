package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.util.log
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.jetbrains.research.kfg.ir.Method as KfgMethod

internal val runs = kexConfig.getIntValue("random-runner", "attempts", 10)

val Method.isStatic get() = (this.modifiers and Modifier.STATIC) == Modifier.STATIC

private fun generate(random: Randomizer, klass: Class<*>, method: Method): Pair<Any?, Array<Any?>?> = try {
    val i = when {
        method.isStatic -> null
        else -> random.next(klass)
    }
    val a = method.genericParameterTypes.map { random.next(it) }.toTypedArray()
    i to a
} catch (e: GenerationException) {
    log.debug("Cannot invoke $method")
    log.debug("Cause: ${e.message}")
    null to null
}


open class RandomRunner(method: KfgMethod, loader: ClassLoader)
    : AbstractRunner(method, loader) {
    private val random = defaultRandomizer

    open fun run(): InvocationResult? {
        val (instance, args) = generate(random, javaClass, javaMethod)
        check(args != null) { "Cannot generate parameters to invoke method $method" }

        return try {
            invoke(instance, args)
        } catch (e: TimeoutException) {
            log.error("Failed method $method with timeout, skipping it")
            null
        }
    }
}

abstract class TracingRandomRunner<T>(method: KfgMethod, loader: ClassLoader)
    : TracingAbstractRunner<T>(method, loader) {
    private val random = defaultRandomizer

    open fun run() : T? {
        val (instance, args) = generate(random, javaClass, javaMethod)
        check(args != null) { "Cannot generate parameters to invoke method $method" }

        return try {
            collectTrace(instance, args)
        } catch (e: TimeoutException) {
            log.error("Failed method $method with timeout, skipping it")
            null
        }
    }
}