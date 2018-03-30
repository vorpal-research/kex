package org.jetbrains.research.kex.runner

import io.github.benas.randombeans.api.EnhancedRandom.random
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun getClass(type: Type): Class<*> = when (type) {
    is BoolType -> Boolean::class.java
    is ByteType -> Byte::class.java
    is ShortType -> Short::class.java
    is IntType -> Int::class.java
    is LongType -> Long::class.java
    is CharType -> Char::class.java
    is FloatType -> Float::class.java
    is DoubleType -> Double::class.java
    else -> try { Class.forName(type.getCanonicalDesc()) } catch (e: ClassNotFoundException) { ClassLoader.getSystemClassLoader().loadClass(type.getCanonicalDesc().drop(1).dropLast(1)) }
}

class Runner(val method: Method, val loader: ClassLoader) : Loggable {
    val `class` = method.`class`
    val out = ByteArrayOutputStream()

    fun run() {
        val javaClass = loader.loadClass(`class`.getFullname().replace('/', '.'))
        val argumentTypes = method.desc.args.map { getClass(it) }.toTypedArray()
        val javaMethod = javaClass.getDeclaredMethod(method.name, *argumentTypes)

        val instance = if (method.isStatic()) null else random(javaClass)
        if (!javaMethod.isAccessible) javaMethod.isAccessible = true
        val args = argumentTypes.map {
            random(it)
        }.toTypedArray()

        log.debug(method)
        log.debug("Instance: $instance")
        log.debug("Generated args: ${args.map { it.toString() }}")

        val oldOut = System.out
        System.setOut(PrintStream(out))
        javaMethod.invoke(instance, *args)
        System.setOut(oldOut)

        log.debug("Invokation output:")
        log.debug(out)
        log.debug()
    }
}