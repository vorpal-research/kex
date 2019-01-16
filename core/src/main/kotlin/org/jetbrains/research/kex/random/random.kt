package org.jetbrains.research.kex.random

import io.github.benas.randombeans.EnhancedRandomBuilder
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

abstract class RandomizerError(msg: String) : Exception(msg)
class GenerationException(msg: String) : RandomizerError(msg)
class UnknownTypeException(msg: String) : RandomizerError(msg)


interface Randomizer {
    /**
     * @return generated object or throws #RandomizerError
     */
    fun next(type: Type): Any?

    /**
     * @return generated object or #null if any exception has occurred
     */
    fun nextOrNull(type: Type) = tryOrNull { next(type) }
}

val defaultRandomizer: Randomizer
    get() = RandomBeansDriver()


class RandomBeansDriver(val config: BeansConfig = defaultConfig) : Randomizer {
    companion object {

        data class BeansConfig(
                val depth: Int,
                val collectionSize: IntRange,
                val stringLength: IntRange,
                val attempts: Int,
                val excludes: List<String>
        )

        val defaultConfig: BeansConfig by lazy {
            val depth = GlobalConfig.getIntValue("random-beans", "depth", 10)
            val minCollectionSize = GlobalConfig.getIntValue("random-beans", "minCollectionSize", 0)
            val maxCollectionSize = GlobalConfig.getIntValue("random-beans", "maxCollectionSize", 1000)
            val minStringLength = GlobalConfig.getIntValue("random-beans", "minStringLength", 0)
            val maxStringLength = GlobalConfig.getIntValue("random-beans", "maxStringLength", 1000)
            val attempts = GlobalConfig.getIntValue("random-beans", "generationAttempts", 1)
            val excludes = GlobalConfig.getMultipleStringValue("random-beans", "exclude")
            BeansConfig(
                    depth = depth,
                    collectionSize = minCollectionSize..maxCollectionSize,
                    stringLength = minStringLength..maxStringLength,
                    attempts = attempts,
                    excludes = excludes
            )
        }
    }

    private val randomizer = EnhancedRandomBuilder.aNewEnhancedRandomBuilder()
            .randomizationDepth(config.depth)
            .collectionSizeRange(config.collectionSize.first, config.collectionSize.last)
            .stringLengthRange(config.stringLength.last, config.stringLength.last)
            .scanClasspathForConcreteTypes(true)
            .exclude(*config.excludes.mapNotNull { tryOrNull { Class.forName(it) } }.toTypedArray())
            .build()

    private fun <T> generateClass(klass: Class<T>) = randomizer.nextObject(klass)

    private fun generateParameterized(type: ParameterizedType): Any? {
        val rawType = type.rawType
        val `object` = next(rawType)
        when (rawType) {
            is Class<*> -> {
                val typeParams = rawType.typeParameters.zip(type.actualTypeArguments).toMap()
                for (it in rawType.declaredFields) {
                    val genType = typeParams[it.genericType as? TypeVariable<*>] ?: it.genericType
                    it.isAccessible = true
                    val value = next(genType)
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
        return next(bounds.first())
    }

    override fun next(type: Type): Any? {
        repeat(config.attempts) {
            tryOrNull {
                return when (type) {
                    is Class<*> -> generateClass(type)
                    is ParameterizedType -> generateParameterized(type)
                    is TypeVariable<*> -> generateTypeVariable(type)
                    else -> throw UnknownTypeException(type.toString())
                }
            }
        }
        throw GenerationException("Unable to next a random instance of type $type")
    }
}