package org.jetbrains.research.kex.reanimator.descriptor

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.reanimator.NoConcreteInstanceException
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.axiom
import org.jetbrains.research.kex.state.predicate.require
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }

val Class.isInstantiable: Boolean
    get() = when {
        visibilityLevel > this.visibility -> false
        this.isAbstract -> false
        this.isInterface -> false
        else -> true
    }

fun KexClass.concreteClass(cm: ClassManager): KexClass {
    val kfgKlass = this.kfgClass(cm.type)
    val concrete = when {
        kfgKlass !is ConcreteClass -> throw NoConcreteInstanceException(kfgKlass)
        kfgKlass.isInstantiable -> kfgKlass
        else -> ConcreteInstanceGenerator[kfgKlass]
    }
    return concrete.kexType
}

fun KexType.concretize(cm: ClassManager): KexType = when (this) {
    is KexClass -> `try` { this.concreteClass(cm) }.getOrDefault(this)
    is KexReference -> KexReference(this.reference.concretize(cm))
    else -> this
}

sealed class Descriptor(term: Term, type: KexType, val hasState: Boolean) {
    var term = term
        protected set

    var type = type
        protected set

    val query: PredicateState get() = collectQuery(mutableSetOf())
    val asString: String get() = print(mutableMapOf())
    val depth: Int get() = countDepth(setOf(), mutableMapOf())

    val typeInfo: PredicateState get() = generateTypeInfo(mutableSetOf())

    operator fun contains(other: Descriptor): Boolean = this.contains(other, mutableSetOf())

    override fun toString() = asString
    infix fun eq(other: Descriptor) = this.structuralEquality(other, mutableSetOf<Pair<Descriptor, Descriptor>>())
    infix fun neq(other: Descriptor) = !(this eq other)

    abstract fun print(map: MutableMap<Descriptor, String>): String
    abstract fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean
    abstract fun collectQuery(set: MutableSet<Descriptor>): PredicateState

    abstract fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, Int>): Int
    abstract fun concretize(cm: ClassManager, visited: MutableSet<Descriptor> = mutableSetOf()): Descriptor
    abstract fun deepCopy(copied: MutableMap<Descriptor, Descriptor> = mutableMapOf()): Descriptor
    abstract fun reduce(visited: MutableSet<Descriptor> = mutableSetOf()): Descriptor
    abstract fun generateTypeInfo(visited: MutableSet<Descriptor> = mutableSetOf()): PredicateState
    abstract fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean
}

sealed class ConstantDescriptor(term: Term, type: KexType) : Descriptor(term, type, false) {
    override fun collectQuery(set: MutableSet<Descriptor>): PredicateState =
        unreachable { log.error("Can't collect query for constant descriptor") }

    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>) = this
    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>) = this
    override fun reduce(visited: MutableSet<Descriptor>) = this
    override fun generateTypeInfo(visited: MutableSet<Descriptor>) = emptyState()
    override fun print(map: MutableMap<Descriptor, String>) = ""
    override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>) =
        this.term == other.term

    override fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, kotlin.Int>) = 1
    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean = this.term == other.term

    object Null : ConstantDescriptor(term { const(null) }, KexNull()) {
        override fun toString() = "null"
    }

    data class Bool(val value: Boolean) : ConstantDescriptor(term { const(value) }, KexBool()) {
        override fun toString() = "$value"
    }

    data class Byte(val value: kotlin.Byte) : ConstantDescriptor(term { const(value) }, KexByte()) {
        override fun toString() = "$value"
    }

    data class Char(val value: kotlin.Char) : ConstantDescriptor(term { const(value) }, KexChar()) {
        override fun toString() = "$value"
    }

    data class Short(val value: kotlin.Short) : ConstantDescriptor(term { const(value) }, KexShort()) {
        override fun toString() = "$value"
    }

    data class Int(val value: kotlin.Int) : ConstantDescriptor(term { const(value) }, KexInt()) {
        override fun toString() = "$value"
    }

    data class Long(val value: kotlin.Long) : ConstantDescriptor(term { const(value) }, KexLong()) {
        override fun toString() = "$value"
    }

    data class Float(val value: kotlin.Float) : ConstantDescriptor(term { const(value) }, KexFloat()) {
        override fun toString() = "$value"
    }

    data class Double(val value: kotlin.Double) : ConstantDescriptor(term { const(value) }, KexDouble()) {
        override fun toString() = "$value"
    }
}

@Suppress("UNCHECKED_CAST")
sealed class FieldContainingDescriptor<T : FieldContainingDescriptor<T>>(
    term: Term,
    klass: KexClass,
    hasState: Boolean
) :
    Descriptor(term, klass, hasState) {
    var klass = klass
        protected set

    val fields = mutableMapOf<Pair<String, KexType>, Descriptor>()

    operator fun get(key: Pair<String, KexType>) = fields[key]
    operator fun get(field: String, type: KexType) = get(field to type)

    operator fun set(key: Pair<String, KexType>, value: Descriptor) {
        fields[key] = value
    }

    operator fun set(field: String, type: KexType, value: Descriptor) = set(field to type, value)

    fun remove(field: String, type: KexType): Descriptor? = fields.remove(field to type)
    fun remove(field: Pair<String, KexType>): Descriptor? = fields.remove(field)

    fun merge(other: T): T {
        val newFields = other.fields + this.fields
        this.fields.clear()
        this.fields.putAll(newFields)
        return this as T
    }

    fun accept(other: T): T {
        val newFields = other.fields.mapValues { it.value.deepCopy(mutableMapOf(other to this)) }
        this.fields.clear()
        this.fields.putAll(newFields)
        return this as T
    }

    override fun print(map: MutableMap<Descriptor, String>): String {
        if (this in map) return ""//map[this]!!
        map[this] = term.name
        return buildString {
            append("$term = $klass {\n")
            for ((field, value) in fields) {
                append("    $field = ${value.term}\n")
            }
            append("}\n")
            for ((_, value) in fields) {
                append(value.print(map))
            }
        }
    }

    override fun collectQuery(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        val builder = StateBuilder()
        builder += axiom { term inequality null }
        for ((field, value) in fields) {
            val fieldTerm = term { term.field(field.second, field.first) }
            if (value.hasState) {
                builder += value.collectQuery(set)
            }
            builder += require { fieldTerm.load() equality value.term }
        }
        return builder.apply()
    }

    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>): T {
        if (this in visited) return this as T
        visited += this

        this.klass = klass.concreteClass(cm)
        this.type = klass
        this.term = term { generate(type) }
        for ((field, value) in fields.toMap()) {
            fields[field] = value.concretize(cm, visited)
        }

        return this as T
    }

    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean {
        if (this in visited) return false
        if (this == other) return true
        visited += this
        if (fields.values.any { it.contains(other, visited) }) return true
        return false
    }

    override fun reduce(visited: MutableSet<Descriptor>): T {
        if (this in visited) return this as T
        visited += this

        for ((field, value) in fields.toMap()) {
            when (value) {
                descriptor { default(field.second) } -> fields.remove(field)
                else -> fields[field] = value.reduce(visited)
            }
        }

        return this as T
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        val instanceOfTerm = term { generate(KexBool()) }
        val builder = StateBuilder()
        builder += axiom { instanceOfTerm equality (term `is` this@FieldContainingDescriptor.type) }
        builder += axiom { instanceOfTerm equality true }
        for ((key, field) in this.fields) {
            val typeInfo = field.generateTypeInfo(visited)
            if (typeInfo.isNotEmpty) {
                builder += state {
                    field.term equality this@FieldContainingDescriptor.term.field(key.second, key.first).load()
                }
                builder += typeInfo
            }
        }
        return builder.apply()
    }

    override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
        if (this == other) return true
        if (other !is ObjectDescriptor) return false
        if (this to other in map) return true
        if (this.klass != other.klass) return false

        map += this to other
        for ((field, type) in this.fields.keys.intersect(other.fields.keys)) {
            val thisValue = this[field, type] ?: return false
            val otherValue = other[field, type] ?: return false
            if (!thisValue.structuralEquality(otherValue, map)) return false
        }
        return true
    }

    override fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, Int>): Int {
        if (this in cache) return cache[this]!!
        if (this in visited) return 0
        val newVisited = visited + this
        var maxDepth = 0
        for (value in fields.values) {
            maxDepth = maxOf(maxDepth, value.countDepth(newVisited, cache))
        }
        cache[this] = maxDepth + 1
        return maxDepth + 1
    }
}

class ObjectDescriptor(klass: KexClass) :
    FieldContainingDescriptor<ObjectDescriptor>(term { generate(klass) }, klass, true) {
    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>): Descriptor {
        if (this in copied) return copied[this]!!
        val copy = ObjectDescriptor(klass)
        copied[this] = copy
        for ((field, value) in fields) {
            copy[field] = value.deepCopy(copied)
        }
        return copy
    }
}

class ClassDescriptor(type: KexClass) : FieldContainingDescriptor<ClassDescriptor>(term { `class`(type) }, type, true) {
    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>): Descriptor {
        if (this in copied) return copied[this]!!
        val copy = ClassDescriptor(type as KexClass)
        copied[this] = copy
        for ((field, value) in fields) {
            copy[field] = value.deepCopy(copied)
        }
        return copy
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        val builder = StateBuilder()
        for ((key, field) in this.fields) {
            val typeInfo = field.generateTypeInfo(visited)
            if (typeInfo.isNotEmpty) {
                builder += state { field.term equality this@ClassDescriptor.term.field(key.second, key.first).load() }
                builder += typeInfo
            }
        }
        return builder.apply()
    }

    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>): ClassDescriptor {
        if (this in visited) return this
        visited += this

        for ((field, value) in fields.toMap()) {
            fields[field] = value.concretize(cm, visited)
        }

        return this
    }

    fun filterFinalFields(cm: ClassManager): ClassDescriptor {
        val kfgClass = klass.kfgClass(cm.type)
        for ((name, type) in fields.keys) {
            val kfgField = kfgClass.getField(name, type.getKfgType(cm.type))
            if (kfgField.isFinal) remove(name, type)
        }
        return this
    }
}

class ArrayDescriptor(val elementType: KexType, val length: Int) :
    Descriptor(term { generate(KexArray(elementType)) }, KexArray(elementType), true) {
    val elements = mutableMapOf<Int, Descriptor>()

    operator fun set(index: Int, value: Descriptor) {
        elements[index] = value
    }

    operator fun get(index: Int) = elements[index]

    override fun print(map: MutableMap<Descriptor, String>): String {
        if (this in map) return ""//map[this]!!
        map[this] = term.name
        return buildString {
            append("$term = $elementType[${this@ArrayDescriptor.length}] {\n")
            for ((index, value) in elements) {
                append("    $index = ${value.term}\n")
            }
            append("}\n")
            for ((_, value) in elements) {
                append(value.print(map))
            }
        }
    }

    override fun collectQuery(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        val builder = StateBuilder()
        builder += axiom { term inequality null }
        elements.forEach { (index, element) ->
            if (element.hasState) {
                builder += element.collectQuery(set)
            }
            builder += require { term[index].load() equality element.term }
        }
        builder += require { term.length() equality const(length) }
        return builder.apply()
    }


    override fun concretize(cm: ClassManager, visited: MutableSet<Descriptor>): ArrayDescriptor {
        if (this in visited) return this
        visited += this
        return this
    }

    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>): Descriptor {
        if (this in copied) return copied[this]!!
        val copy = ArrayDescriptor(elementType, length)
        copied[this] = copy
        for ((index, value) in elements) {
            copy[index] = value.deepCopy(copied)
        }
        return copy
    }

    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean {
        if (this in visited) return false
        if (this == other) return true
        visited += this
        if (elements.values.any { it.contains(other, visited) }) return true
        return false
    }

    override fun reduce(visited: MutableSet<Descriptor>): Descriptor {
        if (this in visited) return this
        visited += this

        for ((index, value) in elements.toMap()) {
            when (value) {
                descriptor { default(elementType) } -> elements.remove(index)
                else -> elements[index] = value.reduce(visited)
            }
        }

        return this
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        val instanceOfTerm = term { generate(KexBool()) }
        val builder = StateBuilder()
        builder += axiom { instanceOfTerm equality (term `is` this@ArrayDescriptor.type) }
        builder += axiom { instanceOfTerm equality true }
        for ((index, element) in this.elements) {
            val typeInfo = element.generateTypeInfo(visited)
            if (typeInfo.isNotEmpty) {
                builder += state { element.term equality this@ArrayDescriptor.term[index].load() }
                builder += typeInfo
            }
        }
        return builder.apply()
    }

    override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
        if (this == other) return true
        if (other !is ArrayDescriptor) return false
        if (this to other in map) return true
        if (this.elementType != other.elementType) return false
        if (this.length != other.length) return false

        map += this to other
        for (index in this.elements.keys.intersect(other.elements.keys)) {
            val thisValue = this[index] ?: return false
            val otherValue = other[index] ?: return false
            if (!thisValue.structuralEquality(otherValue, map)) return false
        }
        return true
    }

    override fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, Int>): Int {
        if (this in cache) return cache[this]!!
        if (this in visited) return 0
        val newVisited = visited + this
        var maxDepth = 0
        for (value in elements.values) {
            maxDepth = maxOf(maxDepth, value.countDepth(newVisited, cache))
        }
        cache[this] = maxDepth + 1
        return maxDepth + 1
    }
}

open class DescriptorBuilder {
    val `null` = ConstantDescriptor.Null
    fun const(@Suppress("UNUSED_PARAMETER") nothing: Nothing?) = `null`
    fun const(value: Boolean) = ConstantDescriptor.Bool(value)
    fun const(number: Number) = when (number) {
        is Byte -> ConstantDescriptor.Byte(number)
        is Short -> ConstantDescriptor.Short(number)
        is Int -> ConstantDescriptor.Int(number)
        is Long -> ConstantDescriptor.Long(number)
        is Float -> ConstantDescriptor.Float(number)
        is Double -> ConstantDescriptor.Double(number)
        else -> unreachable { log.error("Unknown number $number") }
    }

    fun const(char: Char) = ConstantDescriptor.Char(char)
    fun const(klass: KexClass) = ClassDescriptor(klass)

    fun `object`(type: KexClass): ObjectDescriptor = ObjectDescriptor(type)
    fun array(length: Int, elementType: KexType): ArrayDescriptor = ArrayDescriptor(elementType, length)

    fun default(type: KexType, nullable: Boolean): Descriptor = descriptor {
        when (type) {
            is KexBool -> const(false)
            is KexByte -> const(0.toByte())
            is KexChar -> const(0.toChar())
            is KexShort -> const(0.toShort())
            is KexInt -> const(0)
            is KexLong -> const(0L)
            is KexFloat -> const(0.0F)
            is KexDouble -> const(0.0)
            is KexClass -> if (nullable) `null` else `object`(type)
            is KexArray -> if (nullable) `null` else array(0, type.element)
            is KexReference -> default(type.reference, nullable)
            else -> unreachable { log.error("Could not generate default descriptor value for unknown type $type") }
        }
    }

    fun default(type: KexType): Descriptor = default(type, true)
}

fun descriptor(body: DescriptorBuilder.() -> Descriptor): Descriptor =
    DescriptorBuilder().body()