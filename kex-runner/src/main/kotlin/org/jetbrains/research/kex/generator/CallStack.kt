package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

interface ApiCall {
    fun wrap(): CallStack = CallStack(mutableListOf(this))
}

data class CallStack(val stack: MutableList<ApiCall>) : Iterable<ApiCall> by stack {
    constructor() : this(mutableListOf())

    fun add(call: ApiCall): CallStack {
        this.stack += call
        return this
    }

    operator fun plusAssign(call: ApiCall) {
        this.stack += call
    }

    operator fun plusAssign(call: CallStack) {
        this.stack += call.stack
    }

    override fun toString() = stack.joinToString("\n")

    fun reversed(): CallStack = CallStack(stack.reversed().toMutableList())
    fun reverse(): CallStack {
        this.stack.reverse()
        return this
    }
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

data class ExternalConstructorCall(val constructor: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(constructor.isStatic) { log.error("External constructor should be a static method") }
    }

    override fun toString() = "$constructor(${args.joinToString(", ")})"
}

data class MethodCall(val method: Method, val args: List<CallStack>) : ApiCall {
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