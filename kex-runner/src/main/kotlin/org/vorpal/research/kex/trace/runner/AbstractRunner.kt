package org.vorpal.research.kex.trace.runner

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.util.getConstructor
import org.vorpal.research.kex.util.getMethod
import org.vorpal.research.kex.util.runWithTimeout
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.log
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method as ReflectMethod

private val timeout = kexConfig.getLongValue("runner", "timeout", 1000L)

data class InvocationResult(
        val output: ByteArray,
        val error: ByteArray,
        val returnValue: Any? = null,
        val exception: Throwable? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InvocationResult) return false

        if (!output.contentEquals(other.output)) return false
        if (!error.contentEquals(other.error)) return false
        if (returnValue != other.returnValue) return false
        if (exception != other.exception) return false

        return true
    }

    override fun hashCode(): Int {
        var result = output.contentHashCode()
        result = 31 * result + error.contentHashCode()
        result = 31 * result + (returnValue?.hashCode() ?: 0)
        result = 31 * result + (exception?.hashCode() ?: 0)
        return result
    }
}

abstract class AbstractRunner(val method: Method, protected val loader: ClassLoader) {
    protected val javaClass: Class<*> = loader.loadClass(method.klass.canonicalDesc)
    protected val javaMethod by lazy { javaClass.getMethod(method, loader) }
    protected val javaConstructor by lazy { javaClass.getConstructor(method, loader) }

    protected open fun invoke(constructor: Constructor<*>, args: Array<Any?>): InvocationResult {
        val oldOut = System.out
        val oldErr = System.err

        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        var returnValue: Any? = null
        var throwable: Throwable? = null

        try {
            System.setOut(PrintStream(output))
            System.setErr(PrintStream(error))

            constructor.isAccessible = true
            runWithTimeout(timeout) {
                try {
                    returnValue = constructor.newInstance(*args)
                } catch (e: InvocationTargetException) {
                    throwable = e.targetException
                }
            }
        } finally {
            System.setOut(oldOut)
            System.setErr(oldErr)
        }


        if (output.size() != 0) log.debug("Invocation output:\n$output")
        if (error.size() != 0) log.debug("Invocation error:\n$error")
        if (throwable != null)
            log.debug("Invocation exception: $throwable")

        return InvocationResult(output.toByteArray(), error.toByteArray(), returnValue, throwable)
    }

    protected open fun invoke(method: ReflectMethod, instance: Any?, args: Array<Any?>): InvocationResult {
        val oldOut = System.out
        val oldErr = System.err

        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        var returnValue: Any? = null
        var throwable: Throwable? = null

        try {
            System.setOut(PrintStream(output))
            System.setErr(PrintStream(error))

            method.isAccessible = true
            runWithTimeout(timeout) {
                try {
                    returnValue = method.invoke(instance, *args)
                } catch (e: InvocationTargetException) {
                    throwable = e.targetException
                }
            }
        } finally {
            System.setOut(oldOut)
            System.setErr(oldErr)
        }


        if (output.size() != 0) log.debug("Invocation output:\n$output")
        if (error.size() != 0) log.debug("Invocation error:\n$error")
        if (throwable != null)
            log.debug("Invocation exception: $throwable")

        return InvocationResult(output.toByteArray(), error.toByteArray(), returnValue, throwable)
    }

    open fun run(instance: Any?, args: Array<Any?>) = when {
        method.isConstructor -> invoke(javaConstructor, args)
        else -> invoke(javaMethod, instance, args)
    }

    operator fun invoke(instance: Any?, args: Array<Any?>) = run(instance, args)
    open fun invokeStatic(args: Array<Any?>) = invoke(null, args)
}

abstract class TracingAbstractRunner<T>(method: Method, loader: ClassLoader)
    : AbstractRunner(method, loader) {
    abstract fun generateArguments(): Parameters<Any?>?
    abstract fun enableCollector()
    abstract fun disableCollector()
    abstract fun collectTrace(invocationResult: InvocationResult): T

    open fun run(): T? {
        val (instance, args) = generateArguments() ?: return null
        if (!method.isStatic && !method.isConstructor && instance == null) {
            log.error("Cannot generate parameters to invoke method $method")
            return null
        }

        enableCollector()
        val invocationResult = run(instance, args.toTypedArray())
        disableCollector()

        return collectTrace(invocationResult)
    }
}

class DefaultRunner(method: Method, loader: ClassLoader) : AbstractRunner(method, loader)