package org.jetbrains.research.kex.util

import com.abdullin.kthelper.`try`
import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.type.Type
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import java.lang.Class
import java.lang.reflect.*
import java.lang.reflect.Array
import java.net.URLClassLoader
import java.util.*
import org.jetbrains.research.kfg.ir.Class as KfgClass
import org.jetbrains.research.kfg.ir.Field as KfgField
import java.lang.reflect.Method as JMethod

val Class<*>.isAbstract get() = (this.modifiers and Modifier.ABSTRACT) == Modifier.ABSTRACT

val JMethod.isStatic get() = (this.modifiers and Modifier.STATIC) == Modifier.STATIC
val JMethod.isAbstract get() = (this.modifiers and Modifier.ABSTRACT) == Modifier.ABSTRACT

fun ClassLoader.loadClass(tf: TypeFactory, type: KexType): Class<*> = this.loadClass(type.getKfgType(tf))

fun ClassLoader.loadClass(klass: KfgClass): Class<*> =
        this.loadClass(klass.type)

fun ClassLoader.loadClass(type: Type): Class<*> = try {
    when (type) {
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
        else -> throw ClassNotFoundException(type.toString())
    }
} catch (e: NoClassDefFoundError) {
    throw ClassNotFoundException(e.localizedMessage)
}

fun Class<*>.getMethod(method: Method, loader: ClassLoader): java.lang.reflect.Method {
    val argumentTypes = method.argTypes.map { loader.loadClass(it) }.toTypedArray()
    return `try` { this.getDeclaredMethod(method.name, *argumentTypes) }.getOrThrow { throw ClassNotFoundException() }
}

fun Class<*>.getMethod(loader: ClassLoader, name: String, vararg types: Type): java.lang.reflect.Method {
    val argumentTypes = types.map { loader.loadClass(it) }.toTypedArray()
    return `try` { this.getDeclaredMethod(name, *argumentTypes) }.getOrThrow { throw ClassNotFoundException() }
}

fun Class<*>.getConstructor(method: Method, loader: ClassLoader): Constructor<*> {
    require(method.isConstructor)
    val argumentTypes = method.argTypes.map { loader.loadClass(it) }.toTypedArray()
    return `try` { this.getDeclaredConstructor(*argumentTypes) }.getOrThrow { throw ClassNotFoundException() }
}

fun Class<*>.getConstructor(loader: ClassLoader, vararg types: Type): Constructor<*> {
    val argumentTypes = types.map { loader.loadClass(it) }.toTypedArray()
    return `try` { this.getDeclaredConstructor(*argumentTypes) }.getOrThrow { throw ClassNotFoundException() }
}

fun Class<*>.getActualField(name: String): Field {
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

infix fun Field.eq(field: KfgField): Boolean {
    val cl = this.declaringClass.classLoader
    return this.name == field.name && this.type == cl.loadClass(field.type)
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

fun mergeTypes(lhv: java.lang.reflect.Type, rhv: java.lang.reflect.Type, loader: ClassLoader): java.lang.reflect.Type {
    @Suppress("NAME_SHADOWING")
    val lhv = lhv as? Class<*> ?: unreachable { log.error("Don't consider merging other types yet") }
    return when (rhv) {
        is Class<*> -> when {
            lhv.isAssignableFrom(rhv) -> rhv
            rhv.isAssignableFrom(lhv) -> lhv
            else -> findSubtypesOf(loader, lhv, rhv).firstOrNull()
                    ?: unreachable { log.error("Cannot decide on argument type: $rhv or $lhv") }
        }
        is ParameterizedType -> {
            val rawType = rhv.rawType as Class<*>
            // todo: find a way to create a new parameterized type with new raw type
            @Suppress("UNUSED_VARIABLE") val actualType = mergeTypes(lhv, rawType, loader) as Class<*>
            rhv
        }
        is TypeVariable<*> -> {
            val bounds = rhv.bounds
            when {
                bounds == null -> lhv
                bounds.isEmpty() -> lhv
                else -> {
                    require(bounds.size == 1)
                    mergeTypes(lhv, bounds.first(), loader)
                }
            }
        }
        else -> {
            log.warn("Merging unexpected types $lhv and $rhv")
            rhv
        }
    }
}