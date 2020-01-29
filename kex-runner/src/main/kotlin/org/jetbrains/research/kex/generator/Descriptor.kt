package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.require
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.ir.Class as KfgClass
import org.jetbrains.research.kfg.type.Type as KfgType

object TermGenerator {
    private var index = 0

    val nextName: String get() = "generatedTerm${index++}"

    fun nextTerm(type: KexType) = term { value(type, nextName) }
}

sealed class Descriptor {
    abstract val term: Term

    abstract fun toState(ps: PredicateState = emptyState()): PredicateState
}

sealed class ConstantDescriptor : Descriptor() {
    override fun toState(ps: PredicateState) = unreachable<PredicateState> {
        log.error("Can't transform constant descriptor $this to predicate state")
    }

    object Null : ConstantDescriptor() {
        override val term = term { const(null) }
        override fun toString() = "null"
    }

    data class Bool(val value: Boolean) : ConstantDescriptor() {
        override val term get() = term { const(value) }
    }

    data class Int(val value: kotlin.Int) : ConstantDescriptor() {
        override val term get() = term { const(value) }
    }

    data class Long(val value: kotlin.Long) : ConstantDescriptor() {
        override val term get() = term { const(value) }
    }

    data class Float(val value: kotlin.Float) : ConstantDescriptor() {
        override val term get() = term { const(value) }
    }

    data class Double(val value: kotlin.Double) : ConstantDescriptor() {
        override val term get() = term { const(value) }
    }

    data class Class(val value: KfgClass) : ConstantDescriptor() {
        override val term get() = term { `class`(value) }
    }
}

data class FieldDescriptor(
        val name: String,
        val type: KfgType,
        val klass: KfgClass,
        val owner: Descriptor,
        val value: Descriptor
) : Descriptor() {
    override val term get() = term { owner.term.field(type.kexType, name) }

    override fun toState(ps: PredicateState): PredicateState {
        var state = ps
        if (value !is ConstantDescriptor) {
            state = value.toState(state)
        }
        return state.builder().run {
            val tempTerm = term { TermGenerator.nextTerm(this@FieldDescriptor.type.kexType) }
            state { tempTerm equality term.load() }
            require { tempTerm equality value.term }
            apply()
        }
    }

    override fun toString() = "${klass.fullname}.$name = $value"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FieldDescriptor

        if (name != other.name) return false
        if (type != other.type) return false
        if (klass != other.klass) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + klass.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

data class ObjectDescriptor(
        val name: String,
        val klass: KfgClass,
        private val fieldsInner: MutableMap<String, FieldDescriptor> = mutableMapOf()
) : Descriptor() {
    val fields: Map<String, FieldDescriptor> get() = fieldsInner.toMap()

    operator fun set(field: String, value: FieldDescriptor) {
        fieldsInner[field] = value
    }

    operator fun get(field: String) = fieldsInner[field]

    override val term get() = term { value(klass.kexType, name) }

    override fun toState(ps: PredicateState): PredicateState {
        var state = ps
        fields.values.forEach {
            state = it.toState(state)
        }
        return state
    }

    override fun toString(): String = buildString {
        append("$klass $term {")
        if (fieldsInner.isNotEmpty()) {
            append("\n  ")
            appendln(fieldsInner.values.joinToString("\n").replace("\n", "\n  "))
        }
        appendln("}")
    }
}

data class ArrayDescriptor(
        val name: String,
        val length: Int,
        val type: KfgType,
        private val elementsInner: MutableMap<Int, Descriptor> = mutableMapOf()
) : Descriptor() {
    val elements: Map<Int, Descriptor> get() = elementsInner.toMap()
    val elementType = (type as ArrayType).component

    operator fun set(index: Int, value: Descriptor) {
        elementsInner[index] = value
    }

    override val term get() = term { value(type.kexType, name) }

    override fun toState(ps: PredicateState): PredicateState {
        var state = ps
        elements.forEach { (index, element) ->
            state = element.toState(state)
            state += require { term[index] equality element.term }
        }
        return state
    }

    override fun toString(): String = buildString {
        append("$type $term {")
        if (elementsInner.isNotEmpty()) {
            append("\n  ")
            appendln(elementsInner.toList().joinToString("\n") { "[${it.first}] = ${it.second}" }.replace("\n", "\n  "))
        }
        appendln("}")
    }
}

class DescriptorBuilder(val context: ExecutionContext) {
    val `null` = ConstantDescriptor.Null
    fun const(@Suppress("UNUSED_PARAMETER") nothing: Nothing?) = `null`
    fun const(value: Boolean) = ConstantDescriptor.Bool(value)
    fun const(number: Number) = when (number) {
        is Long -> ConstantDescriptor.Long(number)
        is Float -> ConstantDescriptor.Float(number)
        is Double -> ConstantDescriptor.Double(number)
        else -> ConstantDescriptor.Int(number.toInt())
    }

    fun `object`(name: String, type: KfgClass): ObjectDescriptor = ObjectDescriptor(name, type)
    fun array(name: String, length: Int, type: KfgType): ArrayDescriptor = ArrayDescriptor(name, length, type)

    fun field(name: String, type: KfgType, klass: KfgClass, value: Descriptor, owner: ObjectDescriptor) =
            FieldDescriptor(name, type, klass, owner, value)

    fun default(type: KexType, name: String, nullable: Boolean): Descriptor = descriptor(context) {
        when (type) {
            is KexBool -> const(false)
            is KexByte -> const(0)
            is KexChar -> const(0)
            is KexShort -> const(0)
            is KexInt -> const(0)
            is KexLong -> const(0L)
            is KexFloat -> const(0.0F)
            is KexDouble -> const(0.0)
            is KexClass -> if (nullable) `null` else `object`(name, type.kfgClass(context.types))
            is KexArray -> if (nullable) `null` else array(name, 0, type.getKfgType(context.types))
            is KexReference -> default(type.reference, name, nullable)
            else -> unreachable { log.error("Could not generate default descriptor value for unknown type $type") }
        }
    }

    fun default(type: KexType): Descriptor = descriptor(context) {
        when (type) {
            is KexBool -> const(false)
            is KexByte -> const(0)
            is KexChar -> const(0)
            is KexShort -> const(0)
            is KexInt -> const(0)
            is KexLong -> const(0L)
            is KexFloat -> const(0.0F)
            is KexDouble -> const(0.0)
            is KexClass -> `null`
            is KexArray -> `null`
            is KexReference -> default(type.reference)
            else -> unreachable { log.error("Could not generate default descriptor value for unknown type $type") }
        }
    }
}

fun descriptor(context: ExecutionContext, body: DescriptorBuilder.() -> Descriptor): Descriptor =
        DescriptorBuilder(context).body()