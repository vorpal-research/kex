package org.vorpal.research.kex.random.easyrandom

import org.jeasy.random.EasyRandom
import org.jeasy.random.EasyRandomParameters
import org.jeasy.random.ObjectCreationException
import org.jeasy.random.api.ExclusionPolicy
import org.jeasy.random.api.ObjectFactory
import org.jeasy.random.api.RandomizerContext
import org.jeasy.random.util.ReflectionFacade
import org.jeasy.random.util.ReflectionUtils.isAbstract
import org.objenesis.ObjenesisStd
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.random.GenerationException
import org.vorpal.research.kex.random.Randomizer
import org.vorpal.research.kex.random.UnknownTypeException
import org.vorpal.research.kex.util.KfgTargetFilter
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.isAbstract
import org.vorpal.research.kex.util.isPublic
import org.vorpal.research.kex.util.isStatic
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

class EasyRandomDriver(
    val config: BeansConfig = defaultConfig
) : Randomizer() {
    companion object {

        data class BeansConfig(
            val seed: Long,
            val depth: Int,
            val collectionSize: IntRange,
            val stringLength: IntRange,
            val attempts: Int,
            val excludes: Set<KfgTargetFilter>,
            val ignoreErrors: Boolean,
            val bypassSetters: Boolean,
            val ignoreFieldInitializationErrors: Boolean
        )

        val defaultConfig: BeansConfig by lazy {
            val seed = kexConfig.getLongValue("easy-random", "seed", System.currentTimeMillis())
            val depth = kexConfig.getIntValue("easy-random", "depth", 10)
            val minCollectionSize = kexConfig.getIntValue("easy-random", "minCollectionSize", 0)
            val maxCollectionSize = kexConfig.getIntValue("easy-random", "maxCollectionSize", 1000)
            val minStringLength = kexConfig.getIntValue("easy-random", "minStringLength", 0)
            val maxStringLength = kexConfig.getIntValue("easy-random", "maxStringLength", 1000)
            val attempts = kexConfig.getIntValue("easy-random", "generationAttempts", 1)
            val excludes = kexConfig.getMultipleStringValue("easy-random", "exclude")
                .mapTo(mutableSetOf()) { KfgTargetFilter.parse(it) }
            val ignoreErrors = kexConfig.getBooleanValue("easy-random", "ignoreErrors", true)
            val bypassSetters = kexConfig.getBooleanValue("easy-random", "bypassSetters", true)
            val ignoreFieldInitializationErrors =
                kexConfig.getBooleanValue("easy-random", "ignoreFieldInitializationErrors", true)
            BeansConfig(
                seed = seed,
                depth = depth,
                collectionSize = minCollectionSize..maxCollectionSize,
                stringLength = minStringLength..maxStringLength,
                attempts = attempts,
                excludes = excludes,
                ignoreErrors = ignoreErrors,
                bypassSetters = bypassSetters,
                ignoreFieldInitializationErrors = ignoreFieldInitializationErrors
            )
        }
    }

    private val Class<*>.shouldBeExcluded: Boolean
        get() {
            return config.excludes.any { it.matches(name.asmString) }
        }

    private inner class KexReflectionFacade : ReflectionFacade {
        private val reflectionsMap = mutableMapOf<ClassLoader, Reflections>()
        private val defaultReflections by lazy { Reflections() }

        private fun <T> getReflections(type: Class<T>) = type.classLoader?.let {
            reflectionsMap.getOrPut(type.classLoader) {
                Reflections(
                    ConfigurationBuilder()
                        .addClassLoaders(type.classLoader)
                        .setParallel(false)
                )
            }
        } ?: defaultReflections

        override fun <T : Any> getPublicConcreteSubTypesOf(type: Class<T>): List<Class<*>> {
            return getReflections(type).getSubTypesOf(type).filter { it.isPublic && !it.isAbstract }
        }
    }

    private inner class KexObjectFactory : ObjectFactory {
        private val objenesis = ObjenesisStd(false)

        override fun <T> createInstance(type: Class<T>, context: RandomizerContext): T =
            when {
                context.parameters.isScanClasspathForConcreteTypes && isAbstract(type) -> {
                    val reflectionFacade = context.parameters.reflectionFacade
                    val randomConcreteSubType = reflectionFacade.getPublicConcreteSubTypesOf(type)
                        .filterNot { it.shouldBeExcluded }
                        .randomOrNull(this@EasyRandomDriver)
                        ?: throw InstantiationError("Unable to find a matching concrete subtype of type: $type in the classpath")
                    @Suppress("UNCHECKED_CAST")
                    createNewInstance(randomConcreteSubType) as T
                }

                else -> try {
                    createNewInstance(type)
                } catch (e: Error) {
                    throw ObjectCreationException("Unable to create an instance of type: $type", e)
                }
            }

        private fun <T> createNewInstance(type: Class<T>): T = try {
            val noArgConstructor = type.getDeclaredConstructor()
            if (!noArgConstructor.isAccessible) {
                noArgConstructor.isAccessible = true
            }
            noArgConstructor.newInstance()
        } catch (exception: Exception) {
            objenesis.newInstance(type)
        }
    }

    private val randomizerParameters = EasyRandomParameters()
        .seed(config.seed)
        .randomizationDepth(config.depth)
        .collectionSizeRange(config.collectionSize.first, config.collectionSize.last)
        .stringLengthRange(config.stringLength.last, config.stringLength.last)
        .scanClasspathForConcreteTypes(true)
        .exclusionPolicy(object : ExclusionPolicy {
            override fun shouldBeExcluded(field: Field?, ctx: RandomizerContext?): Boolean {
                if (field == null) return true
                return field.type.shouldBeExcluded
            }

            override fun shouldBeExcluded(klass: Class<*>?, ctx: RandomizerContext?): Boolean =
                klass?.shouldBeExcluded ?: true

        })
        .excludeType { type -> type.shouldBeExcluded }
        .ignoreRandomizationErrors(config.ignoreErrors)
        .bypassSetters(true)
        .ignoreFieldInitializationErrors(config.ignoreFieldInitializationErrors)
        .objectFactory(KexObjectFactory())
        .reflectionFacade(KexReflectionFacade())

    private val randomizer = EasyRandom(randomizerParameters)

    private fun <T> generateObject(klass: Class<T>): Any? = randomizer.nextObject(klass)

    private fun <T> generateClass(klass: Class<T>): Any? = when {
        Collection::class.java.isAssignableFrom(klass) -> {
            val cr = CollectionRandomizer.generateCollection(
                klass,
                { generateObject(it) },
                { next(Any::class.java) },
                { nextInt() },
                randomizerParameters.reflectionFacade,
                this
            )
            cr.randomValue
        }

        Map::class.java.isAssignableFrom(klass) -> {
            val mr = CollectionRandomizer.generateMap(
                klass,
                { generateObject(it) },
                { next(Any::class.java) },
                { next(Any::class.java) },
                { nextInt() },
                randomizerParameters.reflectionFacade,
                this
            )
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
                val cr = CollectionRandomizer.generateCollection(
                    rawType,
                    { generateObject(it) },
                    { next(typeParameter) },
                    { nextInt() },
                    randomizerParameters.reflectionFacade,
                    this
                )
                cr.randomValue
            }

            Map::class.java.isAssignableFrom(rawType) -> {
                ktassert(type.actualTypeArguments.size == 2)
                val key = type.actualTypeArguments.first()
                val value = type.actualTypeArguments.last()
                val mr = CollectionRandomizer.generateMap(
                    rawType,
                    { generateObject(it) },
                    { next(key) },
                    { next(value) },
                    { nextInt() },
                    randomizerParameters.reflectionFacade,
                    this
                )
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
        assert(bounds.size == 1) {
            log.debug(
                "Unexpected size of type variable bounds: {}",
                bounds.map { it.typeName }
            )
        }
        return next(bounds.first(), depth)
    }

    private fun getRawType(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> getRawType(type.rawType)
        is GenericArrayType -> Array.newInstance(getRawType(type.genericComponentType), 0).javaClass
        is TypeVariable<*> -> getRawType(type.bounds.first())
        is WildcardType -> getRawType(type.upperBounds.first())
        else -> unreachable { log.error("Unknown type $type") }
    }

    private fun generateType(type: Type, depth: Int): Any? = when (type) {
        is Class<*> -> generateClass(type)
        is ParameterizedType -> generateParameterized(type, depth)
        is GenericArrayType -> {
            val length = config.collectionSize.random()
            val elementType = getRawType(type.genericComponentType)
            val array = Array.newInstance(elementType, length)
            for (i in 0 until length) {
                Array.set(array, i, generateType(type.genericComponentType, depth + 1))
            }
            array
        }
        is TypeVariable<*> -> generateTypeVariable(type, depth)
        is WildcardType -> {
            assert(type.upperBounds.size == 1) { log.debug("Unexpected size of wildcard type upper bounds: {}", type) }
            generateType(type.upperBounds.first(), depth)
        }

        else -> throw UnknownTypeException(type.toString())
    }

    fun next(type: Type, depth: Int): Any? = synchronized(this) {
        if (depth > config.depth) {
            log.warn("Reached maximum depth of generation $depth")
            return null
        }
        repeat(config.attempts) {
            tryOrNull<Unit> {
                return generateType(type, depth)
            }
        }
        throw GenerationException("Unable to next a random instance of type $type")
    }

    override fun next(type: Type): Any? = next(type, depth = 0)
    override fun nextBits(bitCount: Int): Int {
        return randomizer.nextBits(bitCount)
    }
}
