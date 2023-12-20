package org.vorpal.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.vorpal.research.kex.trace.symbolic.WrappedValue
import org.vorpal.research.kex.util.parseValue
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.BoolConstant
import org.vorpal.research.kfg.ir.value.ByteConstant
import org.vorpal.research.kfg.ir.value.CharConstant
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.DoubleConstant
import org.vorpal.research.kfg.ir.value.FloatConstant
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.LongConstant
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kfg.ir.value.ShortConstant

@Suppress("unused")
@InternalSerializationApi
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

@Suppress("unused")
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

internal class WrappedValueSerializer(
    val ctx: NameMapperContext,
    private val methodSerializer: KSerializer<Method>
) : KSerializer<WrappedValue> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("WrappedValue") {
            element("method", methodSerializer.descriptor)
            element<Int>("depth")
            element<String>("name")
        }

    override fun serialize(encoder: Encoder, value: WrappedValue) {
        val output = encoder.beginStructure(descriptor)
        output.encodeSerializableElement(descriptor, 0, methodSerializer, value.method)
        val encodedString = when (val kfgValue = value.value) {
            is Constant -> when (kfgValue) {
                is BoolConstant -> "${kfgValue.value}"
                is ByteConstant -> "${kfgValue.value}b"
                is ShortConstant -> "${kfgValue.value}s"
                is IntConstant -> "${kfgValue.value}"
                is LongConstant -> "${kfgValue.value}L"
                is CharConstant -> "${kfgValue.value.code}c"
                is FloatConstant -> "${kfgValue.value}f"
                is DoubleConstant -> "${kfgValue.value}"
                else -> "${value.value.name}"
            }

            else -> "${value.value.name}"
        }
        output.encodeIntElement(descriptor, 1, value.depth)
        output.encodeStringElement(descriptor, 2, encodedString)
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): WrappedValue {
        val input = decoder.beginStructure(descriptor)
        lateinit var method: Method
        var depth = 0
        lateinit var name: String
        loop@ while (true) {
            when (val i = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> method = input.decodeSerializableElement(descriptor, i, methodSerializer)
                1 -> depth = input.decodeIntElement(descriptor, i)
                2 -> name = input.decodeStringElement(descriptor, i)
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return WrappedValue(method, depth, ctx.getMapper(method).parseValue(name))
    }
}
