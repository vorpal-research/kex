package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.serializer
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kfg.ClassManager

private val prettyPrint = GlobalConfig.getBooleanValue("json", "pretty-print", false)

@UnstableDefault
@ImplicitReflectionSerializer
class KexSerializer(val cm: ClassManager) {
    private val context = getPredicateStateSerialModule(cm)
    private val configuration = JsonConfiguration(
            encodeDefaults = false,
            strictMode = true,
            unquoted = false,
            prettyPrint = prettyPrint,
            useArrayPolymorphism = false,
            classDiscriminator = "className"
    )
    val json = Json(configuration, context)

    inline fun <reified T: Any> toJson(t: T) = json.stringify(T::class.serializer(), t)
    inline fun <reified T: Any> fromJson(str: String) = json.parse(T::class.serializer(), str)
}

