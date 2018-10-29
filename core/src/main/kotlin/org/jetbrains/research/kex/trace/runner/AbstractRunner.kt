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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.*

private val timeout = GlobalConfig.getLongValue("runner", "timeout", 1000L)

class TraceParseError : Exception()

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
        val output = Scanner(ByteArrayInputStream(result.error.toByteArray()))

        val parser = ActionParser()
        val actions = arrayListOf<Action>()
        val tracePrefix = TraceInstrumenter.tracePrefix
        while (output.hasNextLine()) {
            val line = output.nextLine()
            if (line.startsWith(tracePrefix)) {
                val trimmed = line.removePrefix(tracePrefix).drop(1)
                try {
                    actions.add(parser.parseToEnd(trimmed))
                } catch (e: ParseException) {
                    log.error("Failed to parse $method output: $e")
                    log.error("Failed line: $trimmed")
                    throw TraceParseError()
                }
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

        val thread = Thread {
            try {
                result.returnValue = method.invoke(instance, *args)
            } catch (e: InvocationTargetException) {
                log.debug("Invocation exception ${e.targetException}")
                result.exception = e.targetException
            } finally {
                System.setOut(oldOut)
                System.setErr(oldErr)
            }
        }
        thread.start()
        thread.join(timeout)
        @Suppress("DEPRECATION") thread.stop()

        log.debug("Invocation output:\n${result.output}")
        return parse(result)
    }

    open fun run(instance: Any?, args: Array<Any?>) = invoke(javaMethod, instance, args)
    operator fun invoke(instance: Any?, args: Array<Any?>) = run(instance, args)
    open fun invokeStatic(args: Array<Any?>) = run(null, args)
}

class SimpleRunner(method: Method, loader: ClassLoader) : AbstractRunner(method, loader)