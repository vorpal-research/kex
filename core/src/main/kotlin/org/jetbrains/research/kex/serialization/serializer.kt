package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.serializer
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.ClassManager

private val prettyPrint = kexConfig.getBooleanValue("json", "pretty-print", false)

abstract class AbstractSerializer(val context: SerialModule) {
    protected val configuration = JsonConfiguration(
            encodeDefaults = false,
            strictMode = true,
            unquoted = false,
            prettyPrint = prettyPrint,
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

