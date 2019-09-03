package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.trace.UnknownTypeException
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*
import org.reflections.Reflections
import java.lang.reflect.Array

fun ClassLoader.loadClass(type: Type): Class<*> = when (type) {
    is BoolType -> Boolean::class.java
    is ByteType -> Byte::class.java
    is ShortType -> Short::class.java
    is IntType -> Int::class.java
    is LongType -> Long::class.java
    is CharType -> Char::class.java
    is FloatType -> Float::class.java
    is DoubleType -> Double::class.java
    is ArrayType -> try {
        Class.forName(type.canonicalDesc)
    } catch (e: ClassNotFoundException) {
        val element = this.loadClass(type.component)
        // this is fucked up
        val arrayInstance = Array.newInstance(element, 0)
        arrayInstance.javaClass
    }
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

fun findSubtypesOf(vararg classes: Class<*>): Set<Class<*>> {
    val reflections = Reflections()
    val subclasses = classes.map { reflections.getSubTypesOf(it) }
    val allSubclasses = subclasses.flatten().toSet()
    return allSubclasses.filter { klass -> subclasses.all { klass in it } }.toSet()
}