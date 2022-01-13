package org.jetbrains.research.kex.reanimator.actionsequence

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.DescriptorRtMapper
import org.jetbrains.research.kex.ktype.KexRtManager
import org.jetbrains.research.kex.ktype.KexRtManager.rtMapped
import org.jetbrains.research.kex.ktype.KexRtManager.rtUnmapped
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log


sealed class ActionSequence(val name: String) {
    override fun toString(): String = print()
    abstract fun print(): String
    abstract fun clone(): ActionSequence

    internal abstract fun print(builder: StringBuilder, visited: MutableSet<ActionSequence>)
}

class PrimaryValue<T>(val value: T) : ActionSequence(value.toString()) {
    override fun print() = value.toString()
    override fun print(builder: StringBuilder, visited: MutableSet<ActionSequence>) {}
    override fun clone(): ActionSequence = this
}

class UnknownSequence(name: String, val type: Type, val target: Descriptor) : ActionSequence(name) {
    override fun print(): String = "$name = Unknown<$type>($target)"
    override fun print(builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        if (this in visited) return
        visited += this
        builder.appendLine(print())
    }
    override fun clone(): ActionSequence = this
}

class TestCall(name: String, val test: Method, val instance: ActionSequence?, val args: List<ActionSequence>) : ActionSequence(name) {
    override fun print(): String = buildString {
        if (instance != null) append(instance.name).append(".")
        append(test.name)
        append(args.joinToString(prefix = "(", postfix = ")", separator = ",") { it.name })
    }

    override fun print(builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        if (this in visited) return
        visited += this
        instance?.print(builder, visited)
        args.forEach { it.print(builder, visited) }
        builder.appendLine(print())
    }

    override fun clone(): ActionSequence = TestCall(name,test, instance, args)
}

class ActionList(
    name: String,
    val list: MutableList<CodeAction>
) : ActionSequence(name), Iterable<CodeAction> by list {
    constructor(name: String) : this(name, mutableListOf())

    val isComplete: Boolean
        get() {
            val visited = mutableSetOf<ActionSequence>()
            val queue = queueOf<ActionSequence>(this)

            while (queue.isNotEmpty()) {
                val top = queue.poll()
                if (top in visited) continue
                visited += top

                when (top) {
                    is UnknownSequence -> return false
                    is ActionList -> top.flatMap { it.parameters }.forEach {
                        queue += it
                    }
                    else -> {}
                }
            }

            return true
        }

    fun add(call: CodeAction): ActionSequence {
        this.list += call
        return this
    }

    operator fun plusAssign(call: CodeAction) {
        this.list += call
    }

    operator fun plusAssign(calls: List<CodeAction>) {
        this.list += calls
    }

    override fun toString() = print()

    override fun print(): String {
        val builder = StringBuilder()
        this.print(builder, mutableSetOf())
        return builder.toString()
    }

    override fun print(builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        if (this in visited) return
        visited += this
        for (call in list) {
            call.print(this, builder, visited)
        }
    }

    fun reversed() = ActionList(name, list.reversed().toMutableList())
    fun reverse(): ActionList {
        this.list.reverse()
        return this
    }

    override fun clone() = ActionList(name, list.toMutableList())

    fun clear() {
        list.clear()
    }
}

sealed interface CodeAction {
    val parameters: List<ActionSequence>

    fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>)
}

data class DefaultConstructorCall(val klass: Class) : CodeAction {
    override val parameters: List<ActionSequence>
        get() = listOf()

    override fun toString() = "${klass.fullName}()"
    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        builder.appendLine("${owner.name} = $this")
    }
}

data class ConstructorCall(val constructor: Method, val args: List<ActionSequence>) : CodeAction {
    init {
        assert(constructor.isConstructor) { log.error("Trying to create constructor call for non-constructor method") }
    }

    override val parameters: List<ActionSequence> get() = args

    override fun toString() = "$constructor(${args.joinToString(", ")})"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name} = ${constructor.klass.fullName}(${args.joinToString(", ") { it.name }})")
    }
}

data class ExternalConstructorCall(val constructor: Method, val args: List<ActionSequence>) : CodeAction {
    init {
        assert(constructor.isStatic) { log.error("External constructor should be a static method") }
    }

    override val parameters: List<ActionSequence> get() = args

    override fun toString() = "$constructor(${args.joinToString(", ")})"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name} = ${constructor.klass.fullName}.${constructor.name}(${args.joinToString(", ") { it.name }})")
    }
}

data class InnerClassConstructorCall(
    val constructor: Method,
    val outerObject: ActionSequence,
    val args: List<ActionSequence>
) :
    CodeAction {
    override val parameters: List<ActionSequence> get() = listOf(outerObject) + args

    override fun toString() = "${outerObject}.$constructor(${args.joinToString(", ")})"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        outerObject.print(builder, visited)
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name} = ${outerObject.name}.${constructor.klass.fullName}(${args.joinToString(", ") { it.name }})")
    }
}

data class MethodCall(val method: Method, val args: List<ActionSequence>) : CodeAction {
    init {
        assert(!method.isConstructor) { log.error("Trying to create method call for constructor method") }
    }

    override val parameters: List<ActionSequence> get() = args

    override fun toString() = "$method(${args.joinToString(", ")})"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${owner.name}.${method.name}(${args.joinToString(", ") { it.name }})")
    }
}

data class StaticMethodCall(val method: Method, val args: List<ActionSequence>) : CodeAction {
    init {
        assert(!method.isConstructor) { log.error("Trying to create method call for constructor method") }
        assert(method.isStatic) { log.error("Trying to create static method call for non-static method") }
    }

    override val parameters: List<ActionSequence> get() = args

    override fun toString() = "$method(${args.joinToString(", ")})"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        for (arg in args) {
            arg.print(builder, visited)
        }
        builder.appendLine("${method.klass.fullName}.${method.name}(${args.joinToString(", ") { it.name }})")
    }
}

data class StaticFieldSetter(val field: Field, val value: ActionSequence) : CodeAction {
    override val parameters: List<ActionSequence> get() = listOf(value)

    override fun toString() = "${field.klass.fullName}.${field.name} = ${value.name}"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        value.print(builder, visited)
        builder.appendLine(toString())
    }
}

data class FieldSetter(val field: Field, val value: ActionSequence) : CodeAction {
    override val parameters: List<ActionSequence> get() = listOf(value)

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        value.print(builder, visited)
        builder.appendLine("${owner.name}.${field.name} = ${value.name}")
    }
}

data class NewArray(val klass: Type, val length: ActionSequence) : CodeAction {
    val asArray get() = klass as ArrayType
    override val parameters: List<ActionSequence> get() = listOf(length)

    override fun toString() = "new ${asArray.component}[$length]"
    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        length.print(builder, visited)
        builder.appendLine("${owner.name} = new ${asArray.component}[${length.name}]")
    }
}

data class ArrayWrite(val index: ActionSequence, val value: ActionSequence) : CodeAction {
    override val parameters: List<ActionSequence> get() = listOf(value)

    override fun toString() = "array[$index] = $value"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        index.print(builder, visited)
        value.print(builder, visited)
        builder.appendLine("${owner.name}[${index.name}] = ${value.name}")
    }
}

data class EnumValueCreation(val klass: Class, val name: String) : CodeAction {
    override val parameters = listOf<ActionSequence>()

    override fun toString() = "${klass.fullName}.$name"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        builder.appendLine("${owner.name} = ${klass.fullName}.$name")
    }
}

data class StaticFieldGetter(val field: Field) : CodeAction {
    override val parameters = emptyList<ActionSequence>()

    override fun toString() = "${field.klass.fullName}.${field.name}"

    override fun print(owner: ActionSequence, builder: StringBuilder, visited: MutableSet<ActionSequence>) {
        builder.appendLine("${owner.name} = ${field.klass.fullName}.${field.name}")
    }
}

class ActionSequenceRtMapper(val mode: KexRtManager.Mode) {
    private val cache = mutableMapOf<ActionSequence, ActionSequence>()

    fun map(ct: ActionSequence): ActionSequence = when (ct) {
        is PrimaryValue<*> -> ct
        is UnknownSequence -> {
            val mapper = DescriptorRtMapper(mode)
            val newTarget = mapper.map(ct.target)
            UnknownSequence(newTarget.term.toString(), ct.type.mapped, newTarget)
        }
        is TestCall -> TestCall(ct.name, ct.test, ct.instance?.let { map(it) }, ct.args.map { map(it) })
        is ActionList -> if (ct in cache) cache[ct]!!
        else {
            val res = ActionList(ct.name.mapped)
            cache[ct] = res
            for (call in ct) {
                res += map(call)
            }
            res
        }
    }

    private val Class.mapped
        get() = when (mode) {
            KexRtManager.Mode.MAP -> rtMapped
            KexRtManager.Mode.UNMAP -> rtUnmapped
        }

    private val Type.mapped
        get() = when (mode) {
            KexRtManager.Mode.MAP -> rtMapped
            KexRtManager.Mode.UNMAP -> rtUnmapped
        }

    private val String.mapped
        get() = when (mode) {
            KexRtManager.Mode.MAP -> rtMapped
            KexRtManager.Mode.UNMAP -> rtUnmapped
        }

    fun map(api: CodeAction): CodeAction = when (api) {
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
    }
}
