package org.jetbrains.research.kex.runner

import org.jetbrains.research.kex.driver.RandomDriver
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException

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
    else -> TODO()
}

class Runner(val method: Method, val loader: ClassLoader) : Loggable {
    val `class` = method.`class`
    val out = ByteArrayOutputStream()
    val random = RandomDriver()

    fun run() {
        log.debug("Running $method")

        val javaClass = loader.loadClass(`class`.getFullname().replace('/', '.'))
        val argumentTypes = method.desc.args.map { getClass(it, loader) }.toTypedArray()
        val javaMethod = javaClass.getDeclaredMethod(method.name, *argumentTypes)

        val instance = if (method.isStatic()) null else random.generate(javaClass)
        if (!javaMethod.isAccessible) javaMethod.isAccessible = true
        val args = javaMethod.genericParameterTypes.map { random.generate(it) }.toTypedArray()

        log.debug("Instance: $instance")
        log.debug("Generated args: ${args.map { it.toString() }}")

        val oldOut = System.out
        try {
            System.setOut(PrintStream(out))
            javaMethod.invoke(instance, *args)
            System.setOut(oldOut)
            log.debug("Invokation output:")
            log.debug(out)
        } catch (e: InvocationTargetException) {
            System.setOut(oldOut)
            log.debug("Invocation exception ${e.targetException}")
        }
        log.debug()
    }
}