package org.vorpal.research.kex.util

import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.InvalidTypeException
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.*
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import java.lang.Class
import java.lang.reflect.*
import java.lang.reflect.Array
import java.net.URLClassLoader
import java.util.*
import kotlin.Boolean
import kotlin.Byte
import kotlin.Char
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.Short
import kotlin.String
import kotlin.Suppress
import kotlin.also
import kotlin.require
import org.vorpal.research.kfg.ir.Class as KfgClass
import org.vorpal.research.kfg.ir.Field as KfgField
import java.lang.reflect.Method as JMethod
import java.lang.reflect.Type as JType

val ANNOTATION_MODIFIER: Int get() {
    val field1 = Modifier::class.java.getDeclaredField("ANNOTATION")
    field1.isAccessible = true
    return field1.getInt(null)
}

val ENUM_MODIFIER: Int get() {
    val field1 = Modifier::class.java.getDeclaredField("ENUM")
    field1.isAccessible = true
    return field1.getInt(null)
}

val SYNTHETIC_MODIFIER: Int get() {
    val field1 = Modifier::class.java.getDeclaredField("SYNTHETIC")
    field1.isAccessible = true
    return field1.getInt(null)
}

val Class<*>.isAbstract get() = (this.modifiers and Modifier.ABSTRACT) == Modifier.ABSTRACT

val Class<*>.isPublic get() = (this.modifiers and Modifier.PUBLIC) == Modifier.PUBLIC
val Class<*>.isPrivate get() = (this.modifiers and Modifier.PRIVATE) == Modifier.PRIVATE
val Class<*>.isProtected get() = (this.modifiers and Modifier.PROTECTED) == Modifier.PROTECTED

val JMethod.isPublic get() = (this.modifiers and Modifier.PUBLIC) == Modifier.PUBLIC
val JMethod.isPrivate get() = (this.modifiers and Modifier.PRIVATE) == Modifier.PRIVATE
val JMethod.isProtected get() = (this.modifiers and Modifier.PROTECTED) == Modifier.PROTECTED

val Field.isPublic get() = (this.modifiers and Modifier.PUBLIC) == Modifier.PUBLIC
val Field.isPrivate get() = (this.modifiers and Modifier.PRIVATE) == Modifier.PRIVATE
val Field.isProtected get() = (this.modifiers and Modifier.PROTECTED) == Modifier.PROTECTED


fun FieldTerm.isFinal(cm: ClassManager): Boolean {
    val kfgField = cm[this.klass].getField(this.fieldName, this.type.getKfgType(cm.type))
    return kfgField.isFinal
}

val Class<*>.kex: KexType
    get() = when {
        this.isPrimitive -> when (this) {
            Boolean::class.java -> KexBool()
            Byte::class.java -> KexByte()
            Char::class.java -> KexChar()
            Short::class.java -> KexShort()
            Int::class.java -> KexInt()
            Long::class.java -> KexLong()
            Float::class.java -> KexFloat()
            Double::class.java -> KexDouble()
            else -> unreachable { log.error("Unknown primitive type $this") }
        }
        this.isArray -> KexArray(this.componentType.kex)
        else -> KexClass(this.trimmedName.replace('.', '/'))
    }

val Class<*>.allFields
    get(): List<Field> {
        val result = mutableListOf<Field>()
        var current: Class<*>? = this
        do {
            result += current!!.declaredFields
            current = current!!.superclass
        } while (current != null)
        return result
    }

fun Class<*>.getFieldByName(name: String): Field {
    var result: Field?
    var current: Class<*> = this
    do {
        result = `try` { current.getDeclaredField(name) }.getOrNull()
        current = current.superclass ?: break
    } while (result == null)
    return result
        ?: throw NoSuchFieldException()
}

val Field.isStatic: Boolean
    get() = (this.modifiers and Modifier.STATIC) == Modifier.STATIC


var Field.isFinal: Boolean
    get() = (this.modifiers and Modifier.FINAL) == Modifier.FINAL
    set(value) {
        if (value == this.isFinal) return
        val modifiersField = this.javaClass.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(this, this.modifiers and if (value) Modifier.FINAL else Modifier.FINAL.inv())
    }

val JMethod.isStatic get() = (this.modifiers and Modifier.STATIC) == Modifier.STATIC
val JMethod.isAbstract get() = (this.modifiers and Modifier.ABSTRACT) == Modifier.ABSTRACT

val JType.kex
    get() = when (this) {
        is Class<*> -> KexClass(this.canonicalName)
        else -> unreachable { log.error("Unknown java type $this") }
    }

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
            this.loadClass(type.klass.canonicalDesc)
        } catch (e: ClassNotFoundException) {
            ClassLoader.getSystemClassLoader().loadClass(type.klass.canonicalDesc)
        }
        else -> throw ClassNotFoundException(type.toString())
    }
} catch (e: NoClassDefFoundError) {
    throw ClassNotFoundException(e.localizedMessage)
}

fun Class<*>.getMethod(method: Method, loader: ClassLoader): java.lang.reflect.Method {
    val argumentTypes = method.argTypes.map { loader.loadClass(it) }.toTypedArray()
    return `try` {
        this.getDeclaredMethod(method.name, *argumentTypes)
    }.getOrThrow {
        ClassNotFoundException("Could not load method $method", this)
    }
}

fun Class<*>.getMethod(loader: ClassLoader, name: String, desc: String): java.lang.reflect.Method {
    val argumentTypes = parseTypeDesc(loader, desc)
    return `try` {
        this.getDeclaredMethod(name, *argumentTypes.toTypedArray())
    }.getOrThrow {
        ClassNotFoundException("Could not load method $name", this)
    }
}

fun Class<*>.getMethod(loader: ClassLoader, name: String, vararg types: Type): java.lang.reflect.Method {
    val argumentTypes = types.map { loader.loadClass(it) }.toTypedArray()
    return `try` {
        this.getDeclaredMethod(name, *argumentTypes)
    }.getOrThrow {
        ClassNotFoundException("Could not load method $name", this)
    }
}

fun Class<*>.getConstructor(method: Method, loader: ClassLoader): Constructor<*> {
    require(method.isConstructor)
    val argumentTypes = method.argTypes.map { loader.loadClass(it) }.toTypedArray()
    return `try` {
        this.getDeclaredConstructor(*argumentTypes)
    }.getOrThrow {
        ClassNotFoundException("Could not load constructor $method", this)
    }
}

fun Class<*>.getConstructor(loader: ClassLoader, vararg types: Type): Constructor<*> {
    val argumentTypes = types.map { loader.loadClass(it) }.toTypedArray()
    return `try` {
        this.getDeclaredConstructor(*argumentTypes)
    }.getOrThrow {
        ClassNotFoundException("Could not load constructor", this)
    }
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
    return this.eq(cl, field)
}

fun Field.eq(cl: ClassLoader, field: KfgField): Boolean {
    return this.name == field.name && this.type == cl.loadClass(field.type)
}

fun findSubtypesOf(loader: ClassLoader, vararg classes: Class<*>): Set<Class<*>> {
    val reflections = Reflections(
        ConfigurationBuilder()
            .addUrls(classes.mapNotNull { (it.classLoader as? URLClassLoader)?.urLs }.flatMap { it.toList() })
            .addClassLoaders(*classes.map { it.classLoader }.toTypedArray())
            .addClassLoaders(loader)
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

fun parseTypeDesc(classLoader: ClassLoader, desc: String): List<Class<*>> {
    val result = mutableListOf<Class<*>>()
    var index = 0
    while (index < desc.length) {
        result.add(
            when (desc[index]) {
                'V' -> Void::class.javaPrimitiveType!!
                'Z' -> Boolean::class.javaPrimitiveType!!
                'B' -> Byte::class.javaPrimitiveType!!
                'C' -> Char::class.javaPrimitiveType!!
                'S' -> Short::class.javaPrimitiveType!!
                'I' -> Int::class.javaPrimitiveType!!
                'J' -> Long::class.javaPrimitiveType!!
                'F' -> Float::class.javaPrimitiveType!!
                'D' -> Double::class.javaPrimitiveType!!
                'L' -> {
                    val colonIndex = desc.find(index + 1) { it == ';' }
                    if (colonIndex < 0) throw InvalidTypeException(desc)
                    classLoader.loadClass(desc.substring(index + 1, colonIndex)).also {
                        index = colonIndex + 1
                    }
                }
                '[' -> {
                    var level = 0
                    while (desc[index] == '[') {
                        ++level
                        ++index
                    }
                    val colonIndex = desc.find(index + 1) { it == ';' }
                    if (colonIndex < 0) throw InvalidTypeException(desc)
                    val klassType = desc.substring(index, colonIndex)
                    index = colonIndex + 1
                    try {
                        Class.forName(klassType)
                    } catch (e: ClassNotFoundException) {
                        val element = classLoader.loadClass(klassType)
                        // this is fucked up
                        val arrayInstance = Array.newInstance(element, 0)
                        arrayInstance.javaClass
                    }
                }
                else -> unreachable { log.error("Unknown type") }
            }
        )
    }
    return result
}

private fun String.find(startIndex: Int, predicate: (Char) -> Boolean): Int {
    for (i in startIndex until this.length)
        if (predicate(this[i])) return i
    return -1
}