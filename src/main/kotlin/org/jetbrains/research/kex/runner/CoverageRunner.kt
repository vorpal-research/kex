package org.jetbrains.research.kex.runner

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.ParseException
import org.jetbrains.research.kex.UnknownTypeException
import org.jetbrains.research.kex.driver.RandomDriver
import org.jetbrains.research.kex.asm.TraceInstrumenter
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.driver.GenerationException
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kfg.ir.Method as KfgMethod
import org.jetbrains.research.kfg.type.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*

internal val runs = GlobalConfig.getIntValue("runner.runs", 10)
internal val timeout = GlobalConfig.getLongValue("runner.timeout", 1000L)

internal fun getClass(type: Type, loader: ClassLoader): Class<*> = when (type) {
    is BoolType -> Boolean::class.java
    is ByteType -> Byte::class.java
    is ShortType -> Short::class.java
    is IntType -> Int::class.java
    is LongType -> Long::class.java
    is CharType -> Char::class.java
    is FloatType -> Float::class.java
    is DoubleType -> Double::class.java
    is ArrayType -> Class.forName(type.getCanonicalDesc())
    is ClassType -> try {
        loader.loadClass(type.`class`.getFullname().replace('/', '.'))
    } catch (e: ClassNotFoundException) {
        ClassLoader.getSystemClassLoader().loadClass(type.`class`.getFullname().replace('/', '.'))
    }
    else -> throw UnknownTypeException("Unknown type $type")
}

internal class InvocationResult {
    val output = ByteArrayOutputStream()
    val error = ByteArrayOutputStream()
    var exception: Throwable? = null

    operator fun component1() = output
    operator fun component2() = error
    operator fun component3() = exception
}

internal fun invoke(method: Method, instance: Any?, args: Array<Any?>): InvocationResult {
    val log = loggerFor("org.jetbrains.research.kex.runner.invoke")
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
            method.invoke(instance, *args)
        } catch (e: InvocationTargetException) {
            System.setOut(oldOut)
            System.setErr(oldErr)
            log.debug("Invocation exception ${e.targetException}")
            result.exception = e.targetException
        }
    }
    thread.start()
    thread.join(timeout)
    thread.stop()
    if (result.exception == null) {
        System.setOut(oldOut)
        System.setErr(oldErr)
    }

    log.debug("Invocation output:\n${result.output}")
    if (result.error.toString().isNotEmpty()) log.debug("Invocation err: ${result.error}")
    return result
}

class CoverageRunner(val method: KfgMethod, val loader: ClassLoader) : Loggable {
    private val random = RandomDriver()
    private val javaClass: Class<*> = loader.loadClass(method.`class`.getFullname().replace('/', '.'))
    private val javaMethod: java.lang.reflect.Method

    init {
        val argumentTypes = method.desc.args.map { getClass(it, loader) }.toTypedArray()
        javaMethod = javaClass.getDeclaredMethod(method.name, *argumentTypes)
    }

    fun run() = repeat(runs) {
        if (CoverageManager.isBodyCovered(method)) return
        val (instance, args) = try {
            val i = (if (method.isStatic()) null else random.generate(javaClass))
            val a = javaMethod.genericParameterTypes.map { random.generate(it) }.toTypedArray()
            i to a
        } catch (e: GenerationException) {
            log.debug("Cannot invoke $method")
            log.debug("Cause: ${e.message}")
            log.debug("Skipping method")
            return
        }

        val (outputStream, errorStream, exception) = try {
            invoke(javaMethod, instance, args)
        } catch (e: Exception) {
            log.error("Failed when running method $method")
            log.error("Exception: $e")
            val result = InvocationResult()
            result.exception = e
            result
        }

        val output = Scanner(ByteArrayInputStream(outputStream.toByteArray()))
        val error = Scanner(ByteArrayInputStream(errorStream.toByteArray()))

        val parser = ActionParser()
        val actions = mutableListOf<Action>()
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
                    return
                }
            }
        }
        MethodInfo.parse(actions, exception).forEach { CoverageManager.addInfo(it.method, it) }
    }
}