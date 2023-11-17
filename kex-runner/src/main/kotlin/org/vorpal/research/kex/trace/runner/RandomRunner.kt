package org.vorpal.research.kex.trace.runner

import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.random.GenerationException
import org.vorpal.research.kex.random.Randomizer
import org.vorpal.research.kex.util.TimeoutException
import org.vorpal.research.kex.util.getConstructor
import org.vorpal.research.kex.util.getMethod
import org.vorpal.research.kex.util.isStatic
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import sun.misc.Unsafe
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import org.vorpal.research.kfg.ir.Method as KfgMethod

private val UNSAFE = run {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    field.get(null) as Unsafe
}

fun Randomizer.generateParameters(klass: Class<*>, method: Method): Parameters<Any?>? = try {
    val i = when {
        method.isStatic -> null
        else -> next(klass)
    }
    val a = method.genericParameterTypes.mapToArray { next(it) }
    Parameters(i, a.toList(), setOf())
} catch (e: GenerationException) {
    log.debug("Cannot invoke {}", method)
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
    log.debug("Cannot invoke {}", method)
    log.debug("Cause: {}", e.message)
    null
}

private fun default(klass: Class<*>): Any? = when (klass) {
    Boolean::class.javaPrimitiveType -> false
    Byte::class.javaPrimitiveType -> 0.toByte()
    Char::class.javaPrimitiveType -> 0.toChar()
    Short::class.javaPrimitiveType -> 0.toShort()
    Int::class.javaPrimitiveType -> 0
    Long::class.javaPrimitiveType -> 0L
    Float::class.javaPrimitiveType -> 0.0f
    Double::class.javaPrimitiveType -> 0.0
    else -> null
}

private fun defaultNotNull(klass: Class<*>): Any? = when (klass) {
    Boolean::class.javaPrimitiveType -> false
    Byte::class.javaPrimitiveType -> 0.toByte()
    Char::class.javaPrimitiveType -> 0.toChar()
    Short::class.javaPrimitiveType -> 0.toShort()
    Int::class.javaPrimitiveType -> 0
    Long::class.javaPrimitiveType -> 0L
    Float::class.javaPrimitiveType -> 0.0f
    Double::class.javaPrimitiveType -> 0.0
    else -> tryOrNull { UNSAFE.allocateInstance(klass) }
}

fun generateDefaultParameters(method: Constructor<*>): Parameters<Any?>? = try {
    Parameters(
        null,
        method.parameters.map { default(it.type) },
        setOf()
    )
} catch (e: GenerationException) {
    log.debug("Cannot invoke {}", method)
    log.debug("Cause: {}", e.message)
    null
}

fun generateDefaultParameters(klass: Class<*>, method: Method): Parameters<Any?>? = try {
    Parameters(
        defaultNotNull(klass),
        method.parameters.map { default(it.type) },
        setOf()
    )
} catch (e: GenerationException) {
    log.debug("Cannot invoke {}", method)
    log.debug("Cause: {}", e.message)
    null
}

fun Randomizer.generateParameters(loader: ClassLoader, method: KfgMethod): Parameters<Any?>? {
    val klass = loader.loadClass(method.klass)
    return when {
        method.isConstructor -> generateParameters(klass.getConstructor(method, loader))
        else -> generateParameters(klass, klass.getMethod(method, loader))
    }
}

fun generateDefaultParameters(loader: ClassLoader, method: KfgMethod): Parameters<Any?>? {
    val klass = loader.loadClass(method.klass)
    return when {
        method.isConstructor -> generateDefaultParameters(klass.getConstructor(method, loader))
        else -> generateDefaultParameters(klass, klass.getMethod(method, loader))
    }
}

@Suppress("unused")
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
