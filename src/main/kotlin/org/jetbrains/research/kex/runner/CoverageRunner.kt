package org.jetbrains.research.kex.runner

import org.jetbrains.research.kex.UnknownTypeException
import org.jetbrains.research.kex.driver.RandomDriver
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method as KfgMethod
import org.jetbrains.research.kfg.type.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*

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

internal fun invoke(method: Method, instance: Any?, args: Array<Any>): Pair<ByteArrayOutputStream, ByteArrayOutputStream> {
    val log = loggerFor("org.jetbrains.research.kex.runner.invoke")
    log.debug("Running $method")
    log.debug("Instance: $instance")
    log.debug("Args: ${args.map { it.toString() }}")

    val output = ByteArrayOutputStream()
    val error = ByteArrayOutputStream()
    if (!method.isAccessible) method.isAccessible = true

    val oldOut = System.out
    val oldErr = System.err
    try {
        System.setOut(PrintStream(output))
        System.setErr(PrintStream(error))

        method.invoke(instance, *args)

        System.setOut(oldOut)
        System.setErr(oldErr)

        log.debug("Invocation output: $output")
        if (error.toString().isNotEmpty()) log.debug("Invocation err: $error")
    } catch (e: InvocationTargetException) {
        System.setOut(oldOut)
        System.setErr(oldErr)
        log.debug("Invocation exception ${e.targetException}")
        throw e
    }
    return output to error
}

data class InvocationResult(val method: KfgMethod, val instance: Any?, val args: Array<Any>, val coverage: Set<BasicBlock>)

class CoverageRunner(val method: KfgMethod, val loader: ClassLoader) : Loggable {
    private val random = RandomDriver()
    private val javaClass: Class<*> = loader.loadClass(method.`class`.getFullname().replace('/', '.'))
    private val javaMethod: java.lang.reflect.Method

    init {
        val argumentTypes = method.desc.args.map { getClass(it, loader) }.toTypedArray()
        javaMethod = javaClass.getDeclaredMethod(method.name, *argumentTypes)
    }

    fun run() {
        val instance = if (method.isStatic()) null else random.generate(javaClass)
        val args = javaMethod.genericParameterTypes.map { random.generate(it) }.toTypedArray()

        try {
            val (outputStream, errorStream) = invoke(javaMethod, instance, args)

            val output = Scanner(ByteArrayInputStream(outputStream.toByteArray()))
            val error = ByteArrayInputStream(errorStream.toByteArray())

            while (output.hasNextLine()) {
                output.nextLine()
            }
        } catch (e: Exception) {
        }
    }
}