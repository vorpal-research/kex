package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

interface ApiCall {
    fun wrap(): CallStack = CallStack(listOf(this))
}

data class PrimaryValue<T>(val value: T) : ApiCall

data class DefaultConstructorCall(val klass: Class) : ApiCall

data class ConstructorCall(val klass: Class, val constructor: Method, val args: List<CallStack>) : ApiCall {
    init {
        require(constructor.isConstructor) { log.error("Trying to create constructor call for non-constructor method") }
    }
}

data class MethodCall(val instance: CallStack, val method: Method, val args: List<CallStack>) : ApiCall {
    init {
        require(!method.isConstructor) { log.error("Trying to create method call for constructor method") }
    }
}

data class UnknownCall(val target: Descriptor) : ApiCall

data class StaticFieldSetter(val klass: Class, val field: Field, val value: CallStack) : ApiCall

data class FieldSetter(val klass: Class, val owner: CallStack, val field: Field, val value: CallStack) : ApiCall

data class NewArray(val klass: Type, val length: CallStack) : ApiCall

data class ArrayWrite(val array: CallStack, val index: CallStack, val value: CallStack) : ApiCall


data class CallStack(val stack: List<ApiCall>) : Iterable<ApiCall> by stack {
    constructor() : this(listOf())

    fun add(call: ApiCall) = CallStack(stack + call)
    operator fun plus(call: ApiCall) = this.add(call)
    operator fun plus(other: CallStack) = CallStack(this.stack + other.stack)
}
