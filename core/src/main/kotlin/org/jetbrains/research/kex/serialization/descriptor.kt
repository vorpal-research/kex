package org.jetbrains.research.kex.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

@Serializable
enum class DescriptorType {
    CONSTANT, OBJECT, ARRAY, CLASS
}

@ExperimentalSerializationApi
@Serializer(forClass = Descriptor::class)
internal object DescriptorSerializer : KSerializer<Descriptor> {
    val context = mutableMapOf<String, Descriptor>()

    private val fieldsSerializer
        get() = MapSerializer(
            PairSerializer(String.serializer(), KexType.serializer()),
            String.serializer()
        )
    private val elementsSerializer get() = MapSerializer(Int.serializer(), String.serializer())

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("descriptor") {
            element<String>("name")
            element<KexType>("kexType", isOptional = true)
            element<DescriptorType>("descriptorType", isOptional = true)
            element("fields", fieldsSerializer.descriptor, isOptional = true)
            element<Int>("arrayLength", isOptional = true)
            element("arrayElements", elementsSerializer.descriptor, isOptional = true)
        }

    override fun serialize(encoder: Encoder, value: Descriptor) {
        val subDescriptors = when (value) {
            is FieldContainingDescriptor<*> -> value.fields.values
            is ArrayDescriptor -> value.elements.values
            else -> setOf<Descriptor>()
        }
        for (sub in subDescriptors) {
            serialize(encoder, sub)
        }
        val output = encoder.beginStructure(descriptor)
        val name = "${value.term}"
        output.encodeStringElement(descriptor, 0, name)
        if (name in context) {
            output.endStructure(descriptor)
            return
        }
        context[name] = value

        output.encodeSerializableElement(descriptor, 1, KexType.serializer(), value.type)
        when (value) {
            is ObjectDescriptor -> {
                output.encodeSerializableElement(descriptor, 2, DescriptorType.serializer(), DescriptorType.OBJECT)
                output.encodeSerializableElement(
                    descriptor, 3,
                    fieldsSerializer,
                    value.fields.mapValues { "${it.value.term}" }
                )
            }
            is ArrayDescriptor -> {
                output.encodeSerializableElement(descriptor, 2, DescriptorType.serializer(), DescriptorType.ARRAY)
                output.encodeIntElement(descriptor, 4, value.length)
                output.encodeSerializableElement(
                    descriptor, 5,
                    elementsSerializer,
                    value.elements.mapValues { "${it.value.term}" }
                )
            }
            is ClassDescriptor -> {
                output.encodeSerializableElement(descriptor, 2, DescriptorType.serializer(), DescriptorType.CLASS)
                output.encodeSerializableElement(
                    descriptor, 3,
                    fieldsSerializer,
                    value.fields.mapValues { "${it.value.term}" }
                )
            }
            is ConstantDescriptor -> {
                output.encodeSerializableElement(descriptor, 2, DescriptorType.serializer(), DescriptorType.CONSTANT)
            }
        }
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Descriptor {
        val input = decoder.beginStructure(descriptor)
        lateinit var name: String
        lateinit var type: KexType
        lateinit var descriptorType: DescriptorType
        var length = 0
        lateinit var current: Descriptor
        loop@ while (true) {
            when (val i = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> {
                    name = input.decodeStringElement(descriptor, i)
                    if (name in context) return context.getValue(name)
                }
                1 -> type = input.decodeSerializableElement(descriptor, i, KexType.serializer())
                2 -> {
                    descriptorType = input.decodeSerializableElement(descriptor, i, DescriptorType.serializer())
                    if (descriptorType == DescriptorType.CONSTANT) {
                        current = descriptor { const(type, name) }
                        context[name] = current
                    }
                }
                3 -> {
                    current = descriptor {
                        when (descriptorType) {
                            DescriptorType.OBJECT -> `object`(type as KexClass)
                            DescriptorType.CLASS -> const(type as KexClass)
                            else -> unreachable { log.error("Unexpected descriptor type during serialization") }
                        }
                    } as FieldContainingDescriptor<*>
                    val fields = input.decodeSerializableElement(descriptor, i, fieldsSerializer)
                    for ((field, value) in fields)
                        current[field] = context[value] ?: unreachable { log.error("Unknown descriptor") }
                }
                4 -> {
                    ktassert(descriptorType == DescriptorType.ARRAY)
                    length = input.decodeIntElement(descriptor, i)
                    current = descriptor { array(length, ((type as KexArray).element)) }
                }
                5 -> {
                    ktassert(descriptorType == DescriptorType.ARRAY)
                    current as ArrayDescriptor
                    val elements = input.decodeSerializableElement(descriptor, i, elementsSerializer)
                    for ((index, element) in elements)
                        current[index] = context[element] ?: unreachable { log.error("Unknown descriptor") }
                }
            }
        }
        input.endStructure(descriptor)
        return current
    }
}

@ExperimentalSerializationApi
internal inline fun <reified T : Descriptor> DescriptorSerializer.to() = object : KSerializer<T> {
    override val descriptor get() = this@to.descriptor

    override fun deserialize(decoder: Decoder): T = this@to.deserialize(decoder) as T

    override fun serialize(encoder: Encoder, value: T) {
        this@to.serialize(encoder, value)
    }
}