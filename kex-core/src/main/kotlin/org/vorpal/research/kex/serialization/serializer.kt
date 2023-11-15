package org.vorpal.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.value.NameMapperContext

abstract class AbstractSerializer(
    val context: SerializersModule,
    private val prettyPrint: Boolean = true
) {
    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
        prettyPrint = this@AbstractSerializer.prettyPrint
        useArrayPolymorphism = false
        classDiscriminator = "className"
        serializersModule = context
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    inline fun <reified T : Any> toJson(t: T): String =
        json.encodeToString(context.getContextual(T::class) ?: T::class.serializer(), t)

    @ExperimentalSerializationApi
    @InternalSerializationApi
    inline fun <reified T : Any> fromJson(str: String): T =
        json.decodeFromString(context.getContextual(T::class) ?: T::class.serializer(), str)
}

@ExperimentalSerializationApi
@InternalSerializationApi
class KexSerializer(
    val cm: ClassManager,
    val ctx: NameMapperContext = NameMapperContext(),
    prettyPrint: Boolean = true
) : AbstractSerializer(getKexSerialModule(cm, ctx), prettyPrint)

