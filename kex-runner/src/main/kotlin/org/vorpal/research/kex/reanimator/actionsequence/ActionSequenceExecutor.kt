package org.vorpal.research.kex.reanimator.actionsequence

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.asArray
import org.vorpal.research.kex.util.getConstructor
import org.vorpal.research.kex.util.getFieldByName
import org.vorpal.research.kex.util.getMethod
import org.vorpal.research.kex.util.isFinal
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kex.util.runWithTimeout
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import ru.spbstu.wheels.mapToArray
import java.lang.reflect.Array
import java.lang.reflect.Field

private val timeout by lazy {
    kexConfig.getLongValue("runner", "timeout", 1000L)
}

class ActionSequenceExecutor(val ctx: ExecutionContext) {
    private val cache = mutableMapOf<ActionSequence, Any?>()

    private fun Field.setValue(instance: Any?, value: Any?) = if (this.type.isPrimitive) {
        when (value!!.javaClass) {
            Boolean::class.javaObjectType -> this.setBoolean(instance, value as Boolean)
            Byte::class.javaObjectType -> this.setByte(instance, value as Byte)
            Char::class.javaObjectType -> this.setChar(instance, value as Char)
            Short::class.javaObjectType -> this.setShort(instance, value as Short)
            Int::class.javaObjectType -> this.setInt(instance, value as Int)
            Long::class.javaObjectType -> this.setLong(instance, value as Long)
            Float::class.javaObjectType -> this.setFloat(instance, value as Float)
            Double::class.javaObjectType -> this.setDouble(instance, value as Double)
            else -> unreachable { log.error("Trying to get primitive type of non-primitive object $this") }
        }
    } else {
        this.set(instance, value)
    }

    private fun setValue(instance: Any, index: Int, value: Any?) {
        val elementType = instance::class.java.componentType
        if (elementType.isPrimitive) {
            when (value!!.javaClass) {
                Boolean::class.javaObjectType -> Array.setBoolean(instance, index, value as Boolean)
                Byte::class.javaObjectType -> Array.setByte(instance, index, value as Byte)
                Char::class.javaObjectType -> Array.setChar(instance, index, value as Char)
                Short::class.javaObjectType -> Array.setShort(instance, index, value as Short)
                Int::class.javaObjectType -> Array.setInt(instance, index, value as Int)
                Long::class.javaObjectType -> Array.setLong(instance, index, value as Long)
                Float::class.javaObjectType -> Array.setFloat(instance, index, value as Float)
                Double::class.javaObjectType -> Array.setDouble(instance, index, value as Double)
                else -> unreachable { log.error("Trying to get primitive type of non-primitive object $this") }
            }
        } else {
            Array.set(instance, index, value)
        }
    }

    fun execute(actionSequence: ActionSequence): Any? = tryOrNull<Any?> {
        if (actionSequence in cache) return cache[actionSequence]

        var current: Any? = null
        when (actionSequence) {
            is PrimaryValue<*> -> current = actionSequence.value
            is StringValue -> current = actionSequence.value
            is UnknownSequence -> current = null
            is ActionList -> for (call in actionSequence) {
                current = when (call) {
                    is DefaultConstructorCall -> {
                        val reflection = ctx.loader.loadClass(call.klass)
                        val defaultConstructor = reflection.getDeclaredConstructor()
                        defaultConstructor.isAccessible = true
                        var instance: Any? = null
                        runWithTimeout(timeout) {
                            instance = defaultConstructor.newInstance()
                        }
                        cache[actionSequence] = instance
                        instance
                    }
                    is ConstructorCall -> {
                        val reflection = ctx.loader.loadClass(call.constructor.klass)
                        val constructor = reflection.getConstructor(call.constructor, ctx.loader)
                        constructor.isAccessible = true
                        val args = call.args.mapToArray { execute(it) }
                        var instance: Any? = null
                        runWithTimeout(timeout) {
                            instance = constructor.newInstance(*args)
                        }
                        cache[actionSequence] = instance
                        instance
                    }
                    is ExternalConstructorCall -> {
                        val reflection = ctx.loader.loadClass(call.constructor.klass)
                        val javaMethod = reflection.getMethod(call.constructor, ctx.loader)
                        javaMethod.isAccessible = true
                        val args = call.args.mapToArray { execute(it) }
                        var instance: Any? = null
                        runWithTimeout(timeout) {
                            instance = javaMethod.invoke(null, *args)
                        }
                        cache[actionSequence] = instance
                        instance
                    }
                    is ExternalMethodCall -> {
                        val reflection = ctx.loader.loadClass(call.method.klass)
                        val javaMethod = reflection.getMethod(call.method, ctx.loader)
                        javaMethod.isAccessible = true
                        val owner = execute(call.instance)
                        val args = call.args.mapToArray { execute(it) }
                        var instance: Any? = null
                        runWithTimeout(timeout) {
                            instance = javaMethod.invoke(owner, *args)
                        }
                        cache[actionSequence] = instance
                        instance
                    }
                    is InnerClassConstructorCall -> {
                        val reflection = ctx.loader.loadClass(call.constructor.klass)
                        val constructor = reflection.getConstructor(call.constructor, ctx.loader)
                        constructor.isAccessible = true
                        val outerObject = execute(call.outerObject)
                        val args = call.args.mapToArray { execute(it) }
                        var instance: Any? = null
                        runWithTimeout(timeout) {
                            instance = constructor.newInstance(outerObject, *args)
                        }
                        cache[actionSequence] = instance
                        instance
                    }
                    is MethodCall -> {
                        val reflection = ctx.loader.loadClass(call.method.klass)
                        val javaMethod = reflection.getMethod(call.method, ctx.loader)
                        javaMethod.isAccessible = true
                        val args = call.args.mapToArray { execute(it) }
                        runWithTimeout(timeout) {
                            javaMethod.invoke(current, *args)
                        }
                        current
                    }
                    is StaticMethodCall -> {
                        val reflection = ctx.loader.loadClass(call.method.klass)
                        val javaMethod = reflection.getMethod(call.method, ctx.loader)
                        javaMethod.isAccessible = true
                        val args = call.args.mapToArray { execute(it) }
                        runWithTimeout(timeout) {
                            javaMethod.invoke(null, *args)
                        }
                        current
                    }
                    is NewArray -> {
                        val length = execute(call.length)!! as Int
                        val elementReflection = ctx.loader.loadClass(call.asArray.component)
                        Array.newInstance(elementReflection, length)
                    }
                    is ArrayWrite -> {
                        val index = execute(call.index)!! as Int
                        val value = execute(call.value)
                        setValue(current!!, index, value)
                        current
                    }
                    is NewArrayWithInitializer -> {
                        val length = call.elements.size
                        val elementReflection = ctx.loader.loadClass(call.asArray.component)
                        val array = Array.newInstance(elementReflection, length)
                        (0 until length).forEach { setValue(array, it, execute(call.elements[it])) }
                    }
                    is FieldSetter -> {
                        val field = call.field
                        val reflection = ctx.loader.loadClass(field.klass)
                        val fieldReflection = reflection.getFieldByName(field.name)
                        fieldReflection.isAccessible = true
                        fieldReflection.isFinal = false
                        val value = execute(call.value)
                        fieldReflection.setValue(current, value)
                        current
                    }
                    is StaticFieldSetter -> {
                        val field = call.field
                        val reflection = ctx.loader.loadClass(field.klass)
                        val fieldReflection = reflection.getFieldByName(field.name)
                        fieldReflection.isAccessible = true
                        fieldReflection.isFinal = false
                        val value = execute(call.value)
                        fieldReflection.setValue(null, value)
                        current
                    }
                    is EnumValueCreation -> {
                        val reflection = ctx.loader.loadClass(call.klass)
                        val fieldReflect = reflection.getDeclaredField(call.name)
                        fieldReflect.get(null)
                    }
                    is StaticFieldGetter -> {
                        val reflection = ctx.loader.loadClass(call.field.klass)
                        val fieldReflect = reflection.getDeclaredField(call.field.name)
                        fieldReflect.get(null)
                    }
                    is ClassConstantGetter -> ctx.loader.loadClass(call.type)
                    is ArrayClassConstantGetter -> (execute(call.elementType) as Class<*>).asArray()
                }
            }
            else -> log.error { "Unexpected type of action sequence in executor: $actionSequence" }
        }

        return current
    }
}
