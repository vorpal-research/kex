package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.serializer
import org.jetbrains.research.kfg.ClassManager

abstract class AbstractSerializer(val context: SerialModule) {
    @UseExperimental(UnstableDefault::class)
    protected val configuration = JsonConfiguration(
            encodeDefaults = false,
            strictMode = true,
            unquoted = false,
            prettyPrint = true,
            useArrayPolymorphism = false,
            classDiscriminator = "className"
    )
    val json = Json(configuration, context)

    @ImplicitReflectionSerializer
    inline fun <reified T: Any> toJson(t: T) = json.stringify(T::class.serializer(), t)
    @ImplicitReflectionSerializer
    inline fun <reified T: Any> fromJson(str: String) = json.parse(T::class.serializer(), str)
}

@ImplicitReflectionSerializer
class KexSerializer(val cm: ClassManager) : AbstractSerializer(getPredicateStateSerialModule(cm))

