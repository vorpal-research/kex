package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Class as KfgClass
import org.jetbrains.research.kfg.type.Type as KfgType

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
    override val term get() = term { owner.term.field(klass.kexType, name) }

    override fun toState(ps: PredicateState): PredicateState {
        var state = ps
        if (value !is ConstantDescriptor) {
            state = value.toState(state)
        }
        return state.builder().run {
            assume { term equality value.term }
            apply()
        }
    }
}

data class ObjectDescriptor(
        val name: String,
        val klass: KfgClass,
        private val fieldsInner: MutableMap<String, Descriptor> = mutableMapOf()
) : Descriptor() {
    val fields: Map<String, Descriptor> get() = fieldsInner.toMap()

    operator fun set(field: String, value: Descriptor) {
        fieldsInner[field] = value
    }

    override val term get() = term { value(klass.kexType, name) }

    override fun toState(ps: PredicateState): PredicateState {
        var state = ps
        fields.values.forEach {
            state = it.toState(state)
        }
        return state
    }
}

data class ArrayDescriptor(
        val name: String,
        val length: Int,
        val type: KfgType,
        private val elementsInner: MutableMap<Int, Descriptor> = mutableMapOf()
) : Descriptor() {
    val elements: Map<Int, Descriptor> get() = elementsInner.toMap()

    operator fun set(index: Int, value: Descriptor) {
        elementsInner[index] = value
    }

    override val term get() = term { value(type.kexType, name) }

    override fun toState(ps: PredicateState): PredicateState {
        var state = ps
        elements.forEach { (index, element) ->
            state = element.toState(state)
            state += assume { term[index] equality element.term }
        }
        return state
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
}

fun descriptor(context: ExecutionContext, body: DescriptorBuilder.() -> Descriptor): Descriptor =
        DescriptorBuilder(context).body()