@file:Suppress("DuplicatedCode")

package org.vorpal.research.kex.descriptor

import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexJavaClass
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexRtManager
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexRtManager.rtUnmapped
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.emptyState
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.util.StringInfoContext
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import kotlin.random.Random

sealed class Descriptor(term: Term, type: KexType) {
    var term = term
        protected set

    var type = type
        protected set

    private var innerKlassDescriptor: ObjectDescriptor? = null

    var klassDescriptor: ObjectDescriptor
        get() {
            if (innerKlassDescriptor == null) {
                innerKlassDescriptor = descriptor { klass(type) } as ObjectDescriptor
            }
            return innerKlassDescriptor!!
        }
        set(value) {
            innerKlassDescriptor = value
        }

    val query: PredicateState get() = collectQuery(mutableSetOf())
    val initializerState: PredicateState get() = collectInitializerState(mutableSetOf())
    val depth: Int get() = countDepth(setOf(), mutableMapOf())

    val typeInfo: PredicateState get() = generateTypeInfo(mutableSetOf())

    operator fun contains(other: Descriptor): Boolean = this.contains(other, mutableSetOf())

    override fun toString() = print(mutableMapOf())
    infix fun eq(other: Descriptor) = this.structuralEquality(other, mutableSetOf())
    infix fun neq(other: Descriptor) = !(this eq other)

    abstract fun print(map: MutableMap<Descriptor, String>): String
    abstract fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean
    abstract fun collectQuery(set: MutableSet<Descriptor>): PredicateState
    abstract fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState

    abstract fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, Int>): Int
    abstract fun concretize(
        cm: ClassManager,
        accessLevel: AccessModifier,
        random: Random,
        visited: MutableSet<Descriptor> = mutableSetOf()
    ): Descriptor

    abstract fun deepCopy(copied: MutableMap<Descriptor, Descriptor> = mutableMapOf()): Descriptor
    abstract fun reduce(visited: MutableSet<Descriptor> = mutableSetOf()): Descriptor
    abstract fun generateTypeInfo(visited: MutableSet<Descriptor> = mutableSetOf()): PredicateState
    abstract fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean
}

sealed class ConstantDescriptor(term: Term, type: KexType) : Descriptor(term, type) {

    override fun concretize(
        cm: ClassManager,
        accessLevel: AccessModifier,
        random: Random,
        visited: MutableSet<Descriptor>
    ) = this

    override fun deepCopy(copied: MutableMap<Descriptor, Descriptor>) = this
    override fun reduce(visited: MutableSet<Descriptor>) = this
    override fun generateTypeInfo(visited: MutableSet<Descriptor>) = emptyState()

    override fun countDepth(visited: Set<Descriptor>, cache: MutableMap<Descriptor, kotlin.Int>) = 1
    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean = this.term == other.term

    object Null : ConstantDescriptor(term { generate(KexNull()) }, KexNull()) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = null"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality null }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality null }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>) =
            other is Null
    }

    class Bool(val value: Boolean) : ConstantDescriptor(term { generate(KexBool) }, KexBool) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Bool) return false
            return this.value == other.value
        }
    }

    class Byte(val value: kotlin.Byte) : ConstantDescriptor(term { generate(KexByte) }, KexByte) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Byte) return false
            return this.value == other.value
        }
    }

    class Char(val value: kotlin.Char) : ConstantDescriptor(term { generate(KexChar) }, KexChar) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Char) return false
            return this.value == other.value
        }
    }

    class Short(val value: kotlin.Short) : ConstantDescriptor(term { generate(KexShort) }, KexShort) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Short) return false
            return this.value == other.value
        }
    }

    class Int(val value: kotlin.Int) : ConstantDescriptor(term { generate(KexInt) }, KexInt) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Int) return false
            return this.value == other.value
        }
    }

    class Long(val value: kotlin.Long) : ConstantDescriptor(term { generate(KexLong) }, KexLong) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Long) return false
            return this.value == other.value
        }
    }

    class Float(val value: kotlin.Float) : ConstantDescriptor(term { generate(KexFloat) }, KexFloat) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Float) return false
            return this.value == other.value
        }
    }

    class Double(val value: kotlin.Double) : ConstantDescriptor(term { generate(KexDouble) }, KexDouble) {
        override fun print(map: MutableMap<Descriptor, String>) = "$term = $value"

        override fun collectQuery(set: MutableSet<Descriptor>): PredicateState = basic {
            require { term equality value }
        }

        override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState = basic {
            state { term equality value }
        }

        override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
            if (other !is Double) return false
            return this.value == other.value
        }
    }
}

@Suppress("UNCHECKED_CAST")
sealed class FieldContainingDescriptor<T : FieldContainingDescriptor<T>>(
    term: Term,
    klass: KexClass
) :
    Descriptor(term, klass) {
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
            appendLine("$term = $klass {")
            for ((field, value) in fields) {
                appendLine("    $field = ${value.term}")
            }
            append("}")
            for ((_, value) in fields) {
                val valueStr = value.print(map)
                if (valueStr.isNotBlank()) {
                    appendLine()
                    append(valueStr)
                }
            }
        }
    }

    override fun collectQuery(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        return basic {
            axiom { term inequality null }
            for ((field, value) in fields) {
                val fieldTerm = term.field(field.second, field.first)
                append(value.collectQuery(set))
                require { fieldTerm.load() equality value.term }
            }
        }
    }

    override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        return basic {
            state { term.initializeNew() }
            for ((field, value) in fields) {
                val fieldTerm = term.field(field.second, field.first)
                append(value.collectInitializerState(set))
                state { fieldTerm.initialize(value.term) }
            }
        }
    }

    override fun concretize(
        cm: ClassManager,
        accessLevel: AccessModifier,
        random: Random,
        visited: MutableSet<Descriptor>
    ): T {
        if (this in visited) return this as T
        visited += this

        this.klass = instantiationManager.getConcreteClass(klass, cm, accessLevel, random)
        this.type = klass
        this.klassDescriptor["name" to KexJavaClass()] = descriptor { string("$type") }

        this.term = term { generate(type) }
        for ((field, value) in fields.toMap()) {
            fields[field] = value.concretize(cm, accessLevel, random, visited)
        }

        return this as T
    }

    override fun contains(other: Descriptor, visited: MutableSet<Descriptor>): Boolean {
        if (this in visited) return false
        if (this == other) return true
        visited += this
        return fields.values.any { it.contains(other, visited) }
    }

    override fun reduce(visited: MutableSet<Descriptor>): T {
        if (this in visited) return this as T
        visited += this

        for ((field, value) in fields.toMap()) {
            when {
                value eq descriptor { default(field.second) } -> fields.remove(field)
                else -> fields[field] = value.reduce(visited)
            }
        }

        return this as T
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        val instanceOfTerm = term { generate(KexBool) }
        return basic {
            axiom { instanceOfTerm equality (term `is` this@FieldContainingDescriptor.type) }
            axiom { instanceOfTerm equality true }
            for ((key, field) in this@FieldContainingDescriptor.fields) {
                val typeInfo = field.generateTypeInfo(visited)
                if (typeInfo.isNotEmpty) {
                    state {
                        field.term equality this@FieldContainingDescriptor.term.field(key.second, key.first).load()
                    }
                    append(typeInfo)
                }
            }
        }
    }

    override fun structuralEquality(other: Descriptor, map: MutableSet<Pair<Descriptor, Descriptor>>): Boolean {
        if (this == other) return true
        if (other !is FieldContainingDescriptor<*>) return false
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
    FieldContainingDescriptor<ObjectDescriptor>(term { generate(klass) }, klass) {
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

class ClassDescriptor(type: KexClass) :
    FieldContainingDescriptor<ClassDescriptor>(term { staticRef(type) }, type) {
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

        return basic {
            for ((key, field) in this@ClassDescriptor.fields) {
                val typeInfo = field.generateTypeInfo(visited)
                if (typeInfo.isNotEmpty) {
                    state {
                        field.term equality this@ClassDescriptor.term.field(key.second, key.first).load()
                    }
                    append(typeInfo)
                }
            }
        }
    }

    override fun concretize(
        cm: ClassManager,
        accessLevel: AccessModifier,
        random: Random,
        visited: MutableSet<Descriptor>
    ): ClassDescriptor {
        if (this in visited) return this
        visited += this

        for ((field, value) in fields.toMap()) {
            fields[field] = value.concretize(cm, accessLevel, random, visited)
        }

        return this
    }

    fun filterFinalFields(cm: ClassManager): ClassDescriptor {
        val kfgClass = klass.kfgClass(cm.type)
        for ((name, type) in fields.keys.toSet()) {
            val kfgField = kfgClass.getField(name, type.getKfgType(cm.type))
            if (kfgField.isFinal) remove(name, type)
        }
        return this
    }
}

class ArrayDescriptor(val elementType: KexType, val length: Int) :
    Descriptor(term { generate(KexArray(elementType)) }, KexArray(elementType)) {
    val elements = mutableMapOf<Int, Descriptor>()

    operator fun set(index: Int, value: Descriptor) {
        elements[index] = value
    }

    operator fun get(index: Int) = elements[index]

    override fun print(map: MutableMap<Descriptor, String>): String {
        if (this in map) return ""//map[this]!!
        map[this] = term.name
        return buildString {
            appendLine("$term = $elementType[${this@ArrayDescriptor.length}] {")
            for ((index, value) in elements) {
                appendLine("    $index = ${value.term}")
            }
            append("}")
            for ((_, value) in elements) {
                val valueStr = value.print(map)
                if (valueStr.isNotBlank()) {
                    appendLine()
                    append(valueStr)
                }
            }
        }
    }

    override fun collectQuery(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        return basic {
            axiom { term inequality null }
            elements.forEach { (index, element) ->
                append(element.collectQuery(set))
                require { term[index].load() equality element.term }
            }
            require { term.length() equality const(length) }
        }
    }

    override fun collectInitializerState(set: MutableSet<Descriptor>): PredicateState {
        if (this in set) return emptyState()
        set += this
        return basic {
            val fullElements = (0 until length).map { elements[it] ?: descriptor { default(elementType) } }
            fullElements.forEach { append(it.collectInitializerState(set)) }
            state { term.initializeNew(length, fullElements.map { it.term }) }
        }
    }


    override fun concretize(
        cm: ClassManager,
        accessLevel: AccessModifier,
        random: Random,
        visited: MutableSet<Descriptor>
    ): ArrayDescriptor {
        if (this in visited) return this
        visited += this
        for ((_, element) in elements) {
            element.concretize(cm, accessLevel, random, visited)
        }
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
        return elements.values.any { it.contains(other, visited) }
    }

    override fun reduce(visited: MutableSet<Descriptor>): Descriptor {
        if (this in visited) return this
        visited += this

        for ((index, value) in elements.toMap()) {
            when {
                value eq descriptor { default(elementType) } -> elements.remove(index)
                else -> elements[index] = value.reduce(visited)
            }
        }

        return this
    }

    override fun generateTypeInfo(visited: MutableSet<Descriptor>): PredicateState {
        if (this in visited) return emptyState()
        visited += this

        val instanceOfTerm = term { generate(KexBool) }
        return basic {
            axiom { instanceOfTerm equality (term `is` this@ArrayDescriptor.type) }
            axiom { instanceOfTerm equality true }
            for ((index, element) in this@ArrayDescriptor.elements) {
                val typeInfo = element.generateTypeInfo(visited)
                if (typeInfo.isNotEmpty) {
                    state { element.term equality this@ArrayDescriptor.term[index].load() }
                    append(typeInfo)
                }
            }
        }
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
            val res = thisValue.structuralEquality(otherValue, map)
            if (!res) return false
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

open class DescriptorBuilder : StringInfoContext() {
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

    fun const(type: KexType, value: String): Descriptor = descriptor {
        when (type) {
            is KexNull -> const(null)
            is KexBool -> const(value.toBoolean())
            is KexByte -> const(value.toByte())
            is KexChar -> const(value[0])
            is KexShort -> const(value.toShort())
            is KexInt -> const(value.toInt())
            is KexLong -> const(value.toLong())
            is KexFloat -> const(value.toFloat())
            is KexDouble -> const(value.toDouble())
            else -> unreachable { log.error("Could not generate default descriptor value for unknown type $type") }
        }
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

    fun Char.asType(type: KexType): ConstantDescriptor = when (type) {
        KexChar -> const(this)
        KexByte -> const(this.code.toByte())
        else -> unreachable { log.error("Unexpected cast from char to $type") }
    }

    fun string(str: String): Descriptor {
        val string = `object`(KexString())
        val valueArray = array(str.length, valueArrayType.element)
        for (index in str.indices)
            valueArray[index] = str[index].asType(valueArrayType.element)
        string[valueArrayName, valueArrayType] = valueArray
        return string
    }

    fun klass(type: KexType): ObjectDescriptor = klass("$type")

    fun klass(typeName: String): ObjectDescriptor {
        val klass = `object`(KexJavaClass())
        klass["name", KexString()] = string(typeName)
        return klass
    }
}

private object DescriptorBuilderImpl : DescriptorBuilder()

fun descriptor(body: DescriptorBuilder.() -> Descriptor): Descriptor =
    DescriptorBuilderImpl.body()

class DescriptorRtMapper(private val mode: KexRtManager.Mode) : DescriptorBuilder() {
    private val cache = mutableMapOf<Descriptor, Descriptor>()

    private val KexType.mapped
        get() = when (mode) {
            KexRtManager.Mode.MAP -> rtMapped
            KexRtManager.Mode.UNMAP -> rtUnmapped
        }

    fun map(descriptor: Descriptor): Descriptor = cache.getOrElse(descriptor) {
        when (descriptor) {
            is ConstantDescriptor -> descriptor
            is ClassDescriptor -> {
                val klassDesc = const(descriptor.klass.mapped as KexClass)
                cache[descriptor] = klassDesc
                for ((field, value) in descriptor.fields) {
                    klassDesc[field.first, field.second.mapped] = map(value)
                }
                klassDesc
            }

            is ObjectDescriptor -> {
                val objectDesc = `object`(descriptor.klass.mapped as KexClass)
                cache[descriptor] = objectDesc
                for ((field, value) in descriptor.fields) {
                    objectDesc[field.first, field.second.mapped] = map(value)
                }
                objectDesc
            }

            is ArrayDescriptor -> {
                val arrayDesc = array(descriptor.length, descriptor.elementType.mapped)
                cache[descriptor] = arrayDesc
                for ((index, value) in descriptor.elements) {
                    arrayDesc[index] = map(value)
                }
                arrayDesc
            }
        }
    }
}
