package org.jetbrains.research.kex.reanimator.callstack

import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type

interface ApiCall {
    val parameters: List<CallStack>

    fun wrap(name: String): CallStack = CallStack(name, mutableListOf(this))
    fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>)
}

open class CallStack(val name: String, val stack: MutableList<ApiCall>) : Iterable<ApiCall> by stack {
    constructor(name: String) : this(name, mutableListOf())

    val isComplete: Boolean
        get() {
            val visited = mutableSetOf<CallStack>()
            val queue = queueOf(this)

            while (queue.isNotEmpty()) {
                val top = queue.poll()
                if (top in visited) continue
                visited += top

                if (top.any { it is UnknownCall }) {
                    return false
                }

                top.flatMap { it.parameters }.forEach {
                    queue += it
                }
            }

            return true
        }

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

    override fun toString() = print()

    fun print(): String {
        val builder = StringBuilder()
        this.print(builder, mutableSetOf())
        return builder.toString()
    }

    fun print(builder: StringBuilder, visited: MutableSet<CallStack>) {
        if (this in visited) return
        visited += this
        for (call in stack) {
            call.print(this, builder, visited)
        }
    }

    fun reversed(): CallStack = CallStack(name, stack.reversed().toMutableList())
    fun reverse(): CallStack {
        this.stack.reverse()
        return this
    }

    fun clone() = CallStack(name, stack.toMutableList())
}

class PrimaryValue<T>(val value: T) : CallStack(value.toString(), mutableListOf()) {
    override fun toString() = value.toString()
}

data class DefaultConstructorCall(val klass: Class) : ApiCall {
    override val parameters: List<CallStack>
        get() = listOf()

    override fun toString() = "${klass.fullname}()"
    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = $this")
    }
}

data class ConstructorCall(val klass: Class, val constructor: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(constructor.isConstructor) { log.error("Trying to create constructor call for non-constructor method") }
    }

    override val parameters: List<CallStack> get() = args

    override fun toString() = "$constructor(${args.joinToString(", ")})"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name} = ${constructor.`class`.fullname}(${args.joinToString(", ") { it.name }})")
    }
}

data class ExternalConstructorCall(val constructor: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(constructor.isStatic) { log.error("External constructor should be a static method") }
    }

    override val parameters: List<CallStack> get() = args

    override fun toString() = "$constructor(${args.joinToString(", ")})"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name} = ${constructor.`class`.fullname}(${args.joinToString(", ") { it.name }})")
    }
}

data class InnerClassConstructorCall(val constructor: Method, val outerObject: CallStack, val args: List<CallStack>) :
    ApiCall {
    override val parameters: List<CallStack> get() = listOf(outerObject) + args

    override fun toString() = "${outerObject}.$constructor(${args.joinToString(", ")})"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        outerObject.print(builder, visited)
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name} = ${outerObject.name}.${constructor.`class`.fullname}(${args.joinToString(", ") { it.name }})")
    }
}

data class MethodCall(val method: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(!method.isConstructor) { log.error("Trying to create method call for constructor method") }
    }

    override val parameters: List<CallStack> get() = args

    override fun toString() = "$method(${args.joinToString(", ")})"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name}.${method.name}(${args.joinToString(", ") { it.name }})")
    }
}

data class StaticMethodCall(val method: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(!method.isConstructor) { log.error("Trying to create method call for constructor method") }
        assert(method.isStatic) { log.error("Trying to create static method call for non-static method") }
    }

    override val parameters: List<CallStack> get() = args

    override fun toString() = "$method(${args.joinToString(", ")})"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${method.`class`.fullname}.${method.name}(${args.joinToString(", ") { it.name }})")
    }
}

data class UnknownCall(val type: Type, val target: Descriptor) : ApiCall {
    override val parameters: List<CallStack> get() = listOf()

    override fun toString() = "Unknown($target)"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = $this")
    }
}

data class StaticFieldSetter(val klass: Class, val field: Field, val value: CallStack) : ApiCall {
    override val parameters: List<CallStack> get() = listOf(value)

    override fun toString() = "${klass.fullname}.${field.name} = ${value.name}"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        value.print(builder, visited)
        builder.appendLine(toString())
    }
}

data class FieldSetter(val field: Field, val value: CallStack) : ApiCall {
    override val parameters: List<CallStack> get() = listOf(value)

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        value.print(builder, visited)
        builder.appendLine("${owner.name}.${field.name} = ${value.name}")
    }
}

data class NewArray(val klass: Type, val length: CallStack) : ApiCall {
    val asArray get() = klass as ArrayType
    override val parameters: List<CallStack> get() = listOf(length)

    override fun toString() = "new ${asArray.component}[$length]"
    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        length.print(builder, visited)
        builder.appendLine("${owner.name} = new ${asArray.component}[${length.name}]")
    }
}

data class ArrayWrite(val index: CallStack, val value: CallStack) : ApiCall {
    override val parameters: List<CallStack> get() = listOf(value)

    override fun toString() = "array[$index] = $value"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        index.print(builder, visited)
        value.print(builder, visited)
        builder.appendLine("${owner.name}[${index.name}] = ${value.name}")
    }
}

data class EnumValueCreation(val klass: Class, val name: String) : ApiCall {
    override val parameters = listOf<CallStack>()

    override fun toString() = "${klass.fullname}.$name"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = ${klass.fullname}.$name")
    }
}

data class StaticFieldGetter(val klass: Class, val name: String) : ApiCall {
    override val parameters = emptyList<CallStack>()

    override fun toString() = "${klass.fullname}.$name"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = ${klass.fullname}.$name")
    }
}
