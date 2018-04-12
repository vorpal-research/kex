package org.jetbrains.research.kex.driver

import io.github.benas.randombeans.EnhancedRandomBuilder
import io.github.benas.randombeans.api.EnhancedRandom
import io.github.benas.randombeans.api.ObjectGenerationException
import org.jetbrains.research.kex.UnknownTypeException
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.Loggable
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

internal val minCollectionSize = GlobalConfig.getIntValue("random.minCollectionSize", 0)
internal val maxCollectionSize = GlobalConfig.getIntValue("random.maxCollectionSize", 1000)
internal val minStringLength = GlobalConfig.getIntValue("random.minStringLength", 0)
internal val maxStringLength = GlobalConfig.getIntValue("random.maxStringLength", 1000)
internal val attempts = GlobalConfig.getIntValue("random.generationAttempts", 1)
internal val excludes = GlobalConfig.getMultipleStringValue("random.excludes")

class RandomDriver : Loggable {
    private val randomizer: EnhancedRandom = EnhancedRandomBuilder.aNewEnhancedRandomBuilder()
            .scanClasspathForConcreteTypes(true)
            .collectionSizeRange(minCollectionSize, maxCollectionSize)
            .exclude(*excludes.mapNotNull {
                try { Class.forName(it) } catch (e: ClassNotFoundException) { null }
            }.toTypedArray())
            .stringLengthRange(minStringLength, maxStringLength)
            .build()

    private fun <T> generateClass(`class`: Class<T>) = randomizer.nextObject(`class`)

    private fun generateParameterized(type: ParameterizedType): Any? {
        val rawType = type.rawType
        val `object` = generate(rawType)
        if (rawType is Class<*>) {
            val typeParams = rawType.typeParameters.zip(type.actualTypeArguments).toMap()
            for (it in rawType.declaredFields) {
                val genType = typeParams[it.genericType as? TypeVariable<*>] ?: it.genericType
                it.isAccessible = true
                val value = generate(genType)
                it.set(`object`, value)
            }
        } else throw UnknownTypeException("Unknown type $type")
        return `object`
    }

    private fun generateTypeVariable(type: TypeVariable<*>): Any? {
        val bounds = type.bounds
        assert(bounds.size == 1, { log.debug("Unexpected size of type variable bounds: ${bounds.map { it.typeName }}") })
        return generate(bounds.first())
    }

    fun generate(type: Type): Any? {
        repeat(attempts, {
            try {
                return when (type) {
                    is Class<*> -> generateClass(type)
                    is ParameterizedType -> generateParameterized(type)
                    is TypeVariable<*> -> generateTypeVariable(type)
                    else -> throw UnknownTypeException("Unknown type $type")
                }
            } catch (exc: ObjectGenerationException) {
                log.debug("Failed when trying to generate object: ${exc.message}")
            }
        })
        throw ObjectGenerationException("Unable to generate a random instance of type $type")
    }
}