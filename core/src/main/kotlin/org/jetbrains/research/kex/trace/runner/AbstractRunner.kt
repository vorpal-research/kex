package org.jetbrains.research.kex.trace.runner

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.ParseException
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.trace.Action
import org.jetbrains.research.kex.trace.ActionParser
import org.jetbrains.research.kex.trace.Trace
import org.jetbrains.research.kex.util.getClass
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Method
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException

private val timeout = GlobalConfig.getLongValue("runner", "timeout", 1000L)

class TraceParseError : Exception()

private fun runWithTimeout(timeout: Long, body: () -> Unit) {
    val thread = Thread(body)

    thread.start()
    thread.join(timeout)
    if (thread.isAlive) {
        @Suppress("DEPRECATION")
        thread.stop()
    }
}

abstract class AbstractRunner(val method: Method, protected val loader: ClassLoader) {
    protected val javaClass: Class<*> = loader.loadClass(method.`class`.canonicalDesc)
    protected val javaMethod: java.lang.reflect.Method

    init {
        val argumentTypes = method.argTypes.map { getClass(it, loader) }.toTypedArray()
        javaMethod = javaClass.getDeclaredMethod(method.name, *argumentTypes)
    }

    class InvocationResult {
        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        var returnValue: Any? = null
        var exception: Throwable? = null

        operator fun component1() = output
        operator fun component2() = error
        operator fun component3() = exception
    }

    protected fun parse(result: InvocationResult): Trace {
        val lines = String(result.error.toByteArray()).split("\n")

        val parser = ActionParser(method.cm)
        val tracePrefix = TraceInstrumenter.tracePrefix

        val actions = lines
                .filter { it.startsWith(tracePrefix) }
                .map { it.removePrefix(tracePrefix).drop(1) }
                .map {
                    try {
                        parser.parseToEnd(it)
                    } catch (e: ParseException) {
                        log.error("Failed to parse $method output: $e")
                        log.error("Failed line: $it")
                        throw TraceParseError()
                    }
                }

        return Trace.parse(actions, result.exception)
    }

    protected fun invoke(method: java.lang.reflect.Method, instance: Any?, args: Array<Any?>): Trace {
        log.debug("Running $method")
        log.debug("Instance: $instance")
        log.debug("Args: ${args.map { it.toString() }}")

        val result = InvocationResult()
        if (!method.isAccessible) method.isAccessible = true

        val oldOut = System.out
        val oldErr = System.err
        System.setOut(PrintStream(result.output))
        System.setErr(PrintStream(result.error))

        runWithTimeout(timeout) {
            try {
                result.returnValue = method.invoke(instance, *args)
            } catch (e: InvocationTargetException) {
                result.exception = e.targetException
            }
        }

        System.setOut(oldOut)
        System.setErr(oldErr)

        log.debug("Invocation output:\n${result.output}")
        if (result.exception != null)
            log.debug("Invocation exception ${result.exception}")

        return parse(result)
    }

    open fun run(instance: Any?, args: Array<Any?>) = invoke(javaMethod, instance, args)
    operator fun invoke(instance: Any?, args: Array<Any?>) = run(instance, args)
    open fun invokeStatic(args: Array<Any?>) = invoke(null, args)
}

class SimpleRunner(method: Method, loader: ClassLoader) : AbstractRunner(method, loader)