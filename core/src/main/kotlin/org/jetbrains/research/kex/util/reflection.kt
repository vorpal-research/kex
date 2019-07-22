package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.trace.UnknownTypeException
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*

fun ClassLoader.loadClass(type: Type): Class<*> = when (type) {
    is BoolType -> Boolean::class.java
    is ByteType -> Byte::class.java
    is ShortType -> Short::class.java
    is IntType -> Int::class.java
    is LongType -> Long::class.java
    is CharType -> Char::class.java
    is FloatType -> Float::class.java
    is DoubleType -> Double::class.java
    is ArrayType -> Class.forName(type.canonicalDesc)
    is ClassType -> try {
        this.loadClass(type.`class`.canonicalDesc)
    } catch (e: ClassNotFoundException) {
        ClassLoader.getSystemClassLoader().loadClass(type.`class`.canonicalDesc)
    }
    else -> throw UnknownTypeException(type.toString())
}

fun Class<*>.getMethod(method: Method, loader: ClassLoader): java.lang.reflect.Method {
    val argumentTypes = method.argTypes.map { loader.loadClass(it) }.toTypedArray()
    return this.getDeclaredMethod(method.name, *argumentTypes)
}
