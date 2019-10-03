package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.trace.file.UnknownTypeException
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Array
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.*
import java.lang.reflect.Method as JMethod

val Class<*>.isAbstract get() = (this.modifiers and Modifier.ABSTRACT) == Modifier.ABSTRACT

val JMethod.isStatic get() = (this.modifiers and Modifier.STATIC) == Modifier.STATIC
val JMethod.isAbstract get() = (this.modifiers and Modifier.ABSTRACT) == Modifier.ABSTRACT


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

fun Class<*>.getConstructor(method: Method, loader: ClassLoader): Constructor<*> {
    require(method.isConstructor)
    val argumentTypes = method.argTypes.map { loader.loadClass(it) }.toTypedArray()
    return this.getDeclaredConstructor(*argumentTypes)
}

fun Class<*>.getActualField(name: String): java.lang.reflect.Field {
    val queue = ArrayDeque<Class<*>>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val top = queue.pollFirst()
        return try {
            top.getDeclaredField(name)
        } catch (e: NoSuchFieldException) {
            if (top.superclass != null) queue.add(top.superclass)
            queue.addAll(top.interfaces)
            continue
        }
    }
    throw NoSuchFieldException()
}

fun findSubtypesOf(loader: ClassLoader, vararg classes: Class<*>): Set<Class<*>> {
    val reflections = Reflections(
            ConfigurationBuilder()
                    .addUrls(classes.mapNotNull { (it.classLoader as? URLClassLoader)?.urLs }.flatMap { it.toList() })
                    .addClassLoaders(classes.map { it.classLoader })
                    .addClassLoader(loader)
    )
    val subclasses = classes.map { reflections.getSubTypesOf(it) }
    val allSubclasses = subclasses.flatten().toSet()
    return allSubclasses.filter { klass -> subclasses.all { klass in it } }.toSet()
}