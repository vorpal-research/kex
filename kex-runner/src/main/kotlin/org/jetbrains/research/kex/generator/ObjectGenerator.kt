package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.trace.`object`.TraceCollectorProxy
import org.jetbrains.research.kex.util.getConstructor
import org.jetbrains.research.kex.util.getMethod
import org.jetbrains.research.kex.util.loadClass
import java.lang.reflect.Array

// todo: proper identification of objects
class ObjectGenerator(val ctx: ExecutionContext) {
    val cache = mutableMapOf<CallStack, Any?>()

    fun generate(callStack: CallStack): Any? = cache.getOrPut(callStack) {
        // this is fucked up
        TraceCollectorProxy.enableCollector(ctx.cm)
        TraceCollectorProxy.disableCollector()

        var current: Any? = null
        for (call in callStack) {
            current = when (call) {
                is PrimaryValue<*> -> call.value
                is DefaultConstructorCall -> {
                    val reflection = ctx.loader.loadClass(call.klass)
                    val defaultConstructor = reflection.getDeclaredConstructor()
                    defaultConstructor.newInstance()
                }
                is ConstructorCall -> {
                    val reflection = ctx.loader.loadClass(call.klass)
                    val constructor = reflection.getConstructor(call.constructor, ctx.loader)
                    constructor.isAccessible = true
                    constructor.newInstance(*call.args.map { generate(it) }.toTypedArray())
                }
                is MethodCall -> {
                    val reflection = ctx.loader.loadClass(call.method.`class`)
                    val javaMethod = reflection.getMethod(call.method, ctx.loader)
                    javaMethod.isAccessible = true
                    javaMethod.invoke(current, *call.args.map { generate(it) }.toTypedArray())
                    current
                }
                is NewArray -> {
                    val length = generate(call.length)!! as Int
                    val elementReflection = ctx.loader.loadClass(call.klass)
                    Array.newInstance(elementReflection, length)
                }
                is ArrayWrite -> {
//                    current = generate(call.array)
                    val index = generate(call.index)!! as Int
                    val value = generate(call.value)
                    Array.set(current, index, value)
                    current
                }
                is FieldSetter -> {
                    val field = call.field
                    val reflection = ctx.loader.loadClass(field.`class`)
                    val fieldReflection = reflection.getField(field.name)
                    val value = generate(call.value)
                    when {
                        field.isStatic -> fieldReflection.set(null, value)
                        else -> fieldReflection.set(current, value)
                    }
                    current
                }
                else -> null
            }
        }
        current
    }
}