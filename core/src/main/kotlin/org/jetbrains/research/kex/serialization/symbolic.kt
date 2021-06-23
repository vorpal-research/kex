package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

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
