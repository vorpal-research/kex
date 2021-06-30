package org.jetbrains.research.kex.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.research.kex.trace.symbolic.WrappedValue
import org.jetbrains.research.kex.util.parseValue
import org.jetbrains.research.kfg.ir.Method

@InternalSerializationApi
@ExperimentalSerializationApi
inline fun <reified K : Any, reified V : Any, R> mapSerializer(
    crossinline resultDestructor: (R) -> Map<K, V>,
    crossinline resultBuilder: (Map<K, V>) -> R
) = object : KSerializer<R> {
    private val listSerializer = ListSerializer(
        PairSerializer(
            K::class.serializer(),
            V::class.serializer()
        )
    )
    override val descriptor: SerialDescriptor
        get() = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): R {
        return resultBuilder(listSerializer.deserialize(decoder).toMap())
    }

    override fun serialize(encoder: Encoder, value: R) {
        listSerializer.serialize(encoder, resultDestructor(value).toList())
    }
}

@InternalSerializationApi
@ExperimentalSerializationApi
inline fun <reified K : Any, reified V : Any, R> mapSerializer(
    serializersModule: SerializersModule,
    crossinline resultDestructor: (R) -> Map<K, V>,
    crossinline resultBuilder: (Map<K, V>) -> R
) = object : KSerializer<R> {
    private val listSerializer = ListSerializer(
        PairSerializer(
            serializersModule.getContextual(K::class) ?: K::class.serializer(),
            serializersModule.getContextual(V::class) ?: V::class.serializer()
        )
    )
    override val descriptor: SerialDescriptor
        get() = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): R {
        return resultBuilder(listSerializer.deserialize(decoder).toMap())
    }

    override fun serialize(encoder: Encoder, value: R) {
        listSerializer.serialize(encoder, resultDestructor(value).toList())
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = WrappedValue::class)
internal class WrappedValueSerializer(val methodSerializer: KSerializer<Method>) : KSerializer<WrappedValue> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("WrappedValue") {
            element("method", methodSerializer.descriptor)
            element<String>("name")
        }

    override fun serialize(encoder: Encoder, value: WrappedValue) {
        val output = encoder.beginStructure(descriptor)
        output.encodeSerializableElement(descriptor, 0, methodSerializer, value.method)
        output.encodeStringElement(descriptor, 1, "${value.value.name}")
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): WrappedValue {
        val input = decoder.beginStructure(descriptor)
        lateinit var method: Method
        lateinit var name: String
        loop@ while (true) {
            when (val i = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> method = input.decodeSerializableElement(descriptor, i, methodSerializer)
                1 -> name = input.decodeStringElement(descriptor, i)
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return WrappedValue(method, method.slotTracker.parseValue(name))
    }
}
