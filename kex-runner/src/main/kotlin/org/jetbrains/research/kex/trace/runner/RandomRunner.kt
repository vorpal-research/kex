package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kthelper.logging.log
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import org.jetbrains.research.kfg.ir.Method as KfgMethod

fun Randomizer.generateParameters(klass: Class<*>, method: Method): Parameters<Any?>? = try {
    val i = when {
        method.isStatic -> null
        else -> next(klass)
    }
    val a = method.genericParameterTypes.map { next(it) }.toTypedArray()
    Parameters(i, a.toList(), setOf())
} catch (e: GenerationException) {
    log.debug("Cannot invoke $method")
    log.debug("Cause: ${e.message}")
    null
}

fun Randomizer.generateParameters(method: Constructor<*>): Parameters<Any?>? = try {
    Parameters(
        null,
        method.genericParameterTypes.map { next(it) },
        setOf()
    )
} catch (e: GenerationException) {
    log.debug("Cannot invoke $method")
    log.debug("Cause: ${e.message}")
    null
}

fun Randomizer.generateParameters(loader: ClassLoader, method: KfgMethod): Parameters<Any?>? {
    val klass = loader.loadClass(method.klass)
    return when {
        method.isConstructor -> generateParameters(klass.getConstructor(method, loader))
        else -> generateParameters(klass, klass.getMethod(method, loader))
    }
}

open class RandomRunner(
    method: KfgMethod,
    loader: ClassLoader,
    val random: Randomizer
) : AbstractRunner(method, loader) {

    open fun run(): InvocationResult? {
        val (instance, args) = when {
            method.isConstructor -> random.generateParameters(javaConstructor)
            else -> random.generateParameters(javaClass, javaMethod)
        } ?: return null

        return try {
            invoke(instance, args.toTypedArray())
        } catch (e: TimeoutException) {
            log.error("Failed method $method with timeout, skipping it")
            null
        }
    }
}

abstract class TracingRandomRunner<T>(
    method: KfgMethod,
    loader: ClassLoader,
    val random: Randomizer
) : TracingAbstractRunner<T>(method, loader) {

    override fun generateArguments(): Parameters<Any?>? {
        val (instance, args) = when {
            method.isConstructor -> random.generateParameters(javaConstructor)
            else -> random.generateParameters(javaClass, javaMethod)
        } ?: return null

        return Parameters(instance, args.toList(), setOf())
    }
}