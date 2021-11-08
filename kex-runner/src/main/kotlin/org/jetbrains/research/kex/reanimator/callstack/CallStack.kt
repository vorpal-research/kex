package org.jetbrains.research.kex.reanimator.callstack

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.ktype.KexRtManager.rtMapped
import org.jetbrains.research.kex.ktype.KexRtManager.rtUnmapped
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log

sealed interface ApiCall {
    val parameters: List<CallStack>

    fun wrap(name: String): CallStack = CallStack(name, mutableListOf(this))
    fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>)
}

open class CallStack(
    val name: String,
    val stack: MutableList<ApiCall>
) : Iterable<ApiCall> by stack {
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

    override fun toString() = "${klass.fullName}()"
    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = $this")
    }
}

data class ConstructorCall(val constructor: Method, val args: List<CallStack>) : ApiCall {
    init {
        assert(constructor.isConstructor) { log.error("Trying to create constructor call for non-constructor method") }
    }

    override val parameters: List<CallStack> get() = args

    override fun toString() = "$constructor(${args.joinToString(", ")})"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name} = ${constructor.klass.fullName}(${args.joinToString(", ") { it.name }})")
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
        builder.appendLine("${owner.name} = ${constructor.klass.fullName}(${args.joinToString(", ") { it.name }})")
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
        builder.appendLine("${owner.name} = ${outerObject.name}.${constructor.klass.fullName}(${args.joinToString(", ") { it.name }})")
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
        builder.appendLine("${method.klass.fullName}.${method.name}(${args.joinToString(", ") { it.name }})")
    }
}

data class UnknownCall(val type: Type, val target: Descriptor) : ApiCall {
    override val parameters: List<CallStack> get() = listOf()

    override fun toString() = "Unknown($target)"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = $this")
    }
}

data class StaticFieldSetter(val field: Field, val value: CallStack) : ApiCall {
    override val parameters: List<CallStack> get() = listOf(value)

    override fun toString() = "${field.klass.fullName}.${field.name} = ${value.name}"

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

    override fun toString() = "${klass.fullName}.$name"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = ${klass.fullName}.$name")
    }
}

data class StaticFieldGetter(val field: Field) : ApiCall {
    override val parameters = emptyList<CallStack>()

    override fun toString() = "${field.klass.fullName}.${field.name}"

    override fun print(owner: CallStack, builder: StringBuilder, visited: MutableSet<CallStack>) {
        builder.appendLine("${owner.name} = ${field.klass.fullName}.${field.name}")
    }
}

val ApiCall.rtUnmapped: ApiCall get() = when (this) {
    is ArrayWrite -> TODO()
    is ConstructorCall -> TODO()
    is DefaultConstructorCall -> TODO()
    is EnumValueCreation -> TODO()
    is ExternalConstructorCall -> TODO()
    is FieldSetter -> TODO()
    is InnerClassConstructorCall -> TODO()
    is MethodCall -> TODO()
    is NewArray -> TODO()
    is StaticFieldGetter -> TODO()
    is StaticFieldSetter -> TODO()
    is StaticMethodCall -> TODO()
    is UnknownCall -> TODO()
}

class CallStackRtMapper(val mode: Mode) {
    enum class Mode {
        MAP, UNMAP
    }

    private val cache = mutableMapOf<CallStack, CallStack>()

    fun map(ct: CallStack): CallStack {
        if (ct is PrimaryValue<*>) return ct
        if (ct in cache) return cache[ct]!!
        val res = CallStack(ct.name)
        cache[ct] = res
        for (call in ct) {
            res += map(call)
        }
        return res
    }

    private val Class.mapped get() = when (mode) {
        Mode.MAP -> rtMapped
        Mode.UNMAP -> rtUnmapped
    }

    private val Type.mapped get() = when (mode) {
        Mode.MAP -> rtMapped
        Mode.UNMAP -> rtUnmapped
    }

    fun map(api: ApiCall): ApiCall = when (api) {
        is ArrayWrite -> ArrayWrite(map(api.index), map(api.value))
        is ConstructorCall -> {
            val unmappedKlass = api.constructor.klass.mapped
            val unmappedMethod = unmappedKlass.getMethod(
                api.constructor.name,
                api.constructor.returnType.mapped,
                *api.constructor.argTypes.map { it.mapped }.toTypedArray()
            )
            ConstructorCall(unmappedMethod, api.args.map { map(it) })
        }
        is DefaultConstructorCall -> {
            val unmappedKlass = api.klass.mapped
            DefaultConstructorCall(unmappedKlass)
        }
        is EnumValueCreation -> EnumValueCreation(api.klass.mapped, api.name)
        is ExternalConstructorCall -> {
            val unmappedKlass = api.constructor.klass.mapped
            val unmappedMethod = unmappedKlass.getMethod(
                api.constructor.name,
                api.constructor.returnType.mapped,
                *api.constructor.argTypes.map { it.mapped }.toTypedArray()
            )
            ExternalConstructorCall(unmappedMethod, api.args.map { map(it) })
        }
        is FieldSetter -> {
            val unmappedKlass = api.field.klass.mapped
            val unmappedField = unmappedKlass.getField(api.field.name, api.field.type.mapped)
            FieldSetter(unmappedField, map(api.value))
        }
        is InnerClassConstructorCall -> {
            val unmappedKlass = api.constructor.klass.mapped
            val unmappedMethod = unmappedKlass.getMethod(
                api.constructor.name,
                api.constructor.returnType.mapped,
                *api.constructor.argTypes.map { it.mapped }.toTypedArray()
            )
            InnerClassConstructorCall(unmappedMethod,
                map(api.outerObject), api.args.map { map(it) })
        }
        is MethodCall -> {
            val unmappedKlass = api.method.klass.mapped
            val unmappedMethod = unmappedKlass.getMethod(
                api.method.name,
                api.method.returnType.mapped,
                *api.method.argTypes.map { it.mapped }.toTypedArray()
            )
            MethodCall(unmappedMethod, api.args.map { map(it) })
        }
        is NewArray -> NewArray(api.klass.mapped, map(api.length))
        is StaticFieldGetter -> {
            val unmappedKlass = api.field.klass.mapped
            val unmappedField = unmappedKlass.getField(api.field.name, api.field.type.mapped)
            StaticFieldGetter(unmappedField)
        }
        is StaticFieldSetter -> {
            val unmappedKlass = api.field.klass.mapped
            val unmappedField = unmappedKlass.getField(api.field.name, api.field.type.mapped)
            StaticFieldSetter(unmappedField, map(api.value))
        }
        is StaticMethodCall -> {
            val unmappedKlass = api.method.klass.mapped
            val unmappedMethod = unmappedKlass.getMethod(
                api.method.name,
                api.method.returnType.mapped,
                *api.method.argTypes.map { it.mapped }.toTypedArray()
            )
            StaticMethodCall(unmappedMethod, api.args.map { map(it) })
        }
        is UnknownCall -> api
    }
}
