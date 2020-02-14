package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

interface ApiCall {
    fun wrap(): CallStack = CallStack(listOf(this))
}

data class PrimaryValue<T>(val value: T) : ApiCall

data class DefaultConstructorCall(val klass: Class) : ApiCall {
    override fun toString() = "${klass.fullname}()"
}

data class ConstructorCall(val klass: Class, val constructor: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(constructor.isConstructor) { log.error("Trying to create constructor call for non-constructor method") }
    }

    override fun toString() = "$constructor(${args.joinToString(", ")})"
}

data class MethodCall(val instance: CallStack, val method: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(!method.isConstructor) { log.error("Trying to create method call for constructor method") }
    }

    override fun toString() = "$method(${args.joinToString(", ")})"
}

data class UnknownCall(val klass: Class, val target: Descriptor) : ApiCall {
    override fun toString() = "Unknown($target)"
}

data class StaticFieldSetter(val klass: Class, val field: Field, val value: CallStack) : ApiCall {
    override fun toString() = "${klass.fullname}.${field.name} = $value"
}

data class FieldSetter(val klass: Class, val owner: CallStack, val field: Field, val value: CallStack) : ApiCall

data class NewArray(val klass: Type, val length: CallStack) : ApiCall {
    override fun toString() = "new $klass[$length]"
}

data class ArrayWrite(val array: CallStack, val index: CallStack, val value: CallStack) : ApiCall {
    override fun toString() = "array[$index] = $value"
}



data class CallStack(val stack: List<ApiCall>) : Iterable<ApiCall> by stack {
    constructor() : this(listOf())

    fun add(call: ApiCall) = CallStack(stack + call)
    operator fun plus(call: ApiCall) = this.add(call)
    operator fun plus(other: CallStack) = CallStack(this.stack + other.stack)

    override fun toString() = stack.joinToString("\n")
}
