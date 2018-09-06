package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.trace.UnknownTypeError
import org.jetbrains.research.kfg.type.*

fun getClass(type: Type, loader: ClassLoader): Class<*> = when (type) {
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
        loader.loadClass(type.`class`.canonicalDesc)
    } catch (e: ClassNotFoundException) {
        ClassLoader.getSystemClassLoader().loadClass(type.`class`.canonicalDesc)
    }
    else -> throw UnknownTypeError(type.toString())
}
