package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.jetbrains.research.kfg.ClassManager

abstract class AbstractSerializer(val context: SerializersModule) {
    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
        prettyPrint = true
        useArrayPolymorphism = false
        classDiscriminator = "className"
        serializersModule = context
    }

    abstract fun preCondition()
    abstract fun postCondition()

    @ExperimentalSerializationApi
    @InternalSerializationApi
    inline fun <reified T : Any> toJson(t: T): String = try {
        preCondition()
        json.encodeToString(context.getContextual(T::class) ?: T::class.serializer(), t)
    } finally {
        postCondition()
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    inline fun <reified T: Any> fromJson(str: String): T = try {
        preCondition()
        json.decodeFromString(context.getContextual(T::class) ?: T::class.serializer(), str)
    } finally {
        postCondition()
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
class KexSerializer(val cm: ClassManager) : AbstractSerializer(getKexSerialModule(cm)) {
    override fun preCondition() {}
    override fun postCondition() {
        DescriptorSerializer.context.clear()
    }
}

