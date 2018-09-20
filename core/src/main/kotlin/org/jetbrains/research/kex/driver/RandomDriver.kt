package org.jetbrains.research.kex.driver

import io.github.benas.randombeans.EnhancedRandomBuilder
import io.github.benas.randombeans.api.EnhancedRandom
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

internal val depth = GlobalConfig.getIntValue("random", "depth", 10)
internal val minCollectionSize = GlobalConfig.getIntValue("random", "minCollectionSize", 0)
internal val maxCollectionSize = GlobalConfig.getIntValue("random", "maxCollectionSize", 1000)
internal val minStringLength = GlobalConfig.getIntValue("random", "minStringLength", 0)
internal val maxStringLength = GlobalConfig.getIntValue("random", "maxStringLength", 1000)
internal val attempts = GlobalConfig.getIntValue("random", "generationAttempts", 1)
internal val excludes = GlobalConfig.getMultipleStringValue("random", "excludes")

abstract class RandomEngineError(msg: String) : Exception(msg)

class GenerationException(msg: String) : RandomEngineError(msg)

class UnknownTypeException(msg: String) : RandomEngineError(msg)


object RandomDriver {
    private val randomizer: EnhancedRandom = EnhancedRandomBuilder.aNewEnhancedRandomBuilder()
            .scanClasspathForConcreteTypes(true)
            .collectionSizeRange(minCollectionSize, maxCollectionSize)
            .exclude(*excludes.mapNotNull {
                try { Class.forName(it) } catch (e: ClassNotFoundException) { null }
            }.toTypedArray())
            .randomizationDepth(depth)
            .stringLengthRange(minStringLength, maxStringLength)
            .build()

    private fun <T> generateClass(`class`: Class<T>) = randomizer.nextObject(`class`)

    private fun generateParameterized(type: ParameterizedType): Any? {
        val rawType = type.rawType
        val `object` = generate(rawType)
        when (rawType) {
            is Class<*> -> {
                val typeParams = rawType.typeParameters.zip(type.actualTypeArguments).toMap()
                for (it in rawType.declaredFields) {
                    val genType = typeParams[it.genericType as? TypeVariable<*>] ?: it.genericType
                    it.isAccessible = true
                    val value = generate(genType)
                    it.set(`object`, value)
                }
            }
            else -> throw UnknownTypeException(type.toString())
        }
        return `object`
    }

    private fun generateTypeVariable(type: TypeVariable<*>): Any? {
        val bounds = type.bounds
        require(bounds.size == 1) { log.debug("Unexpected size of type variable bounds: ${bounds.map { it.typeName }}") }
        return generate(bounds.first())
    }

    fun generate(type: Type): Any? {
        repeat(attempts) {
            tryOrNull {
                return when (type) {
                    is Class<*> -> generateClass(type)
                    is ParameterizedType -> generateParameterized(type)
                    is TypeVariable<*> -> generateTypeVariable(type)
                    else -> throw UnknownTypeException(type.toString())
                }
            }
        }
        throw GenerationException("Unable to generate a random instance of type $type")
    }

    fun generateOrNull(type: Type): Any? = try { generate(type) } catch (e: GenerationException) { null }
}