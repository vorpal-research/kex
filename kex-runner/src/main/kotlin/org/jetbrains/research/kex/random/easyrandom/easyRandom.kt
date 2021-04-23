package org.jetbrains.research.kex.random.easyrandom

import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull
import org.jeasy.random.EasyRandom
import org.jeasy.random.EasyRandomParameters
import org.jeasy.random.ObjectCreationException
import org.jeasy.random.api.ObjectFactory
import org.jeasy.random.api.RandomizerContext
import org.jeasy.random.util.CollectionUtils.randomElementOf
import org.jeasy.random.util.ReflectionUtils.getPublicConcreteSubTypesOf
import org.jeasy.random.util.ReflectionUtils.isAbstract
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.random.UnknownTypeException
import org.jetbrains.research.kex.util.isStatic
import org.jetbrains.research.kfg.Package
import org.objenesis.ObjenesisStd
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

class EasyRandomDriver(val config: BeansConfig = defaultConfig) : Randomizer {
    companion object {

        data class BeansConfig(
                val depth: Int,
                val collectionSize: IntRange,
                val stringLength: IntRange,
                val attempts: Int,
                val excludes: Set<Package>
        )

        val defaultConfig: BeansConfig by lazy {
            val depth = kexConfig.getIntValue("easy-random", "depth", 10)
            val minCollectionSize = kexConfig.getIntValue("easy-random", "minCollectionSize", 0)
            val maxCollectionSize = kexConfig.getIntValue("easy-random", "maxCollectionSize", 1000)
            val minStringLength = kexConfig.getIntValue("easy-random", "minStringLength", 0)
            val maxStringLength = kexConfig.getIntValue("easy-random", "maxStringLength", 1000)
            val attempts = kexConfig.getIntValue("easy-random", "generationAttempts", 1)
            val excludes = kexConfig.getMultipleStringValue("easy-random", "exclude").map { Package.parse(it) }.toSet()
            BeansConfig(
                    depth = depth,
                    collectionSize = minCollectionSize..maxCollectionSize,
                    stringLength = minStringLength..maxStringLength,
                    attempts = attempts,
                    excludes = excludes
            )
        }
    }

    private class KexObjectFactory : ObjectFactory {
        private val objenesis = ObjenesisStd(false)

        override fun <T> createInstance(type: Class<T>, context: RandomizerContext): T =
                when {
                    context.parameters.isScanClasspathForConcreteTypes && isAbstract<T>(type) -> {
                        val randomConcreteSubType = randomElementOf<Class<*>>(getPublicConcreteSubTypesOf<T>(type))
                        if (randomConcreteSubType == null) {
                            throw InstantiationError("Unable to find a matching concrete subtype of type: $type in the classpath")
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            createNewInstance(randomConcreteSubType) as T
                        }
                    }
                    else -> try {
                        createNewInstance(type)
                    } catch (e: Error) {
                        throw ObjectCreationException("Unable to create an instance of type: $type", e)
                    }
                }

        private fun <T> createNewInstance(type: Class<T>): T = try {
            val noArgConstructor = type.getDeclaredConstructor()
            @Suppress("DEPRECATION")
            if (!noArgConstructor.isAccessible) {
                noArgConstructor.isAccessible = true
            }
            noArgConstructor.newInstance()
        } catch (exception: Exception) {
            objenesis.newInstance(type)
        }
    }

    private val randomizer = EasyRandom(
            EasyRandomParameters()
                    .seed(System.currentTimeMillis())
                    .randomizationDepth(config.depth)
                    .collectionSizeRange(config.collectionSize.first, config.collectionSize.last)
                    .stringLengthRange(config.stringLength.last, config.stringLength.last)
                    .scanClasspathForConcreteTypes(true)
                    .excludeType { type -> config.excludes.any { it.isParent(Package.parse(type.name)) } }
                    .objectFactory(KexObjectFactory())
    )

    private fun <T> generateObject(klass: Class<T>): Any? = randomizer.nextObject(klass)

    private fun <T> generateClass(klass: Class<T>): Any? = when {
        Collection::class.java.isAssignableFrom(klass) -> {
            val cr = CollectionRandomizer.generateCollection(klass, { generateObject(it) }, { next(Any::class.java) })
            cr.randomValue
        }
        Map::class.java.isAssignableFrom(klass) -> {
            val mr = CollectionRandomizer.generateMap(klass, { generateObject(it) }, { next(Any::class.java) }, { next(Any::class.java) })
            mr.randomValue
        }
        else -> generateObject(klass)
    }

    private fun generateParameterized(type: ParameterizedType, depth: Int): Any? {
        val rawType = type.rawType as? Class<*> ?: throw UnknownTypeException(type.toString())
        return when {
            Collection::class.java.isAssignableFrom(rawType) -> {
                ktassert(type.actualTypeArguments.size == 1)
                val typeParameter = type.actualTypeArguments.first()
                val cr = CollectionRandomizer.generateCollection(rawType, { generateObject(it) }, { next(typeParameter) })
                cr.randomValue
            }
            Map::class.java.isAssignableFrom(rawType) -> {
                ktassert(type.actualTypeArguments.size == 2)
                val key = type.actualTypeArguments.first()
                val value = type.actualTypeArguments.last()
                val mr = CollectionRandomizer.generateMap(rawType, { generateObject(it) }, { next(key) }, { next(value) })
                mr.randomValue
            }
            else -> {
                val obj = next(rawType, depth)
                if (obj != null) {
                    val typeParams = rawType.typeParameters.zip(type.actualTypeArguments).toMap()
                    for (it in rawType.declaredFields.filterNot { it.isStatic }) {
                        val genType = typeParams[it.genericType as? TypeVariable<*>] ?: it.genericType
                        it.isAccessible = true
                        val value = next(genType, depth + 1)
                        it.set(obj, value)
                    }
                }
                obj
            }
        }
    }

    private fun generateTypeVariable(type: TypeVariable<*>, depth: Int): Any? {
        val bounds = type.bounds
        assert(bounds.size == 1) { log.debug("Unexpected size of type variable bounds: ${bounds.map { it.typeName }}") }
        return next(bounds.first(), depth)
    }

    private fun generateType(type: Type, depth: Int): Any? = when (type) {
        is Class<*> -> generateClass(type)
        is ParameterizedType -> generateParameterized(type, depth)
        is TypeVariable<*> -> generateTypeVariable(type, depth)
        is WildcardType -> {
            assert(type.upperBounds.size == 1) { log.debug("Unexpected size of wildcard type upper bounds: $type") }
            generateType(type.upperBounds.first(), depth)
        }
        else -> throw UnknownTypeException(type.toString())
    }

    fun next(type: Type, depth: Int): Any? {
        if (depth > config.depth) {
            log.warn("Reached maximum depth of generation $depth")
            return null
        }
        repeat(config.attempts) {
            tryOrNull {
                return generateType(type, depth)
            }
        }
        throw GenerationException("Unable to next a random instance of type $type")
    }

    override fun next(type: Type): Any? = next(type, depth = 0)
}