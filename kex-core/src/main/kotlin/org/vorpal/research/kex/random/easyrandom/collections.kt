package org.vorpal.research.kex.random.easyrandom

import org.jeasy.random.api.Randomizer
import org.jeasy.random.util.ReflectionFacade
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.isAbstract
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet

private infix fun Int.`in`(range: IntRange) = range.first + (this % (range.last - range.first))

abstract class CollectionRandomizer<T>(
    val collection: () -> MutableCollection<T>,
    private val delegate: Randomizer<T>,
    intRandomizer: Randomizer<Int>
) : Randomizer<Collection<T>> {
    private val nbElements: Int = intRandomizer.randomValue `in` collectionSizeRange

    private fun getRandomElement(): T = delegate.randomValue

    override fun toString(): String {
        return "CollectionRandomizer [impl=$collection, delegate=$delegate, nbElements=$nbElements]"
    }

    override fun getRandomValue(): MutableCollection<T> {
        val collection = collection()
        for (i in 0 until nbElements) {
            collection.add(getRandomElement())
        }
        return collection
    }

    companion object {
        val collectionSizeRange: IntRange by lazy {
            val minCollectionSize = kexConfig.getIntValue("easy-random", "minCollectionSize", 0)
            val maxCollectionSize = kexConfig.getIntValue("easy-random", "maxCollectionSize", 1000)
            minCollectionSize..maxCollectionSize
        }

        fun <T> generateCollection(
            klass: Class<*>,
            random: (Class<*>) -> Any?,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ) = when {
            List::class.java.isAssignableFrom(klass) -> when {
                klass.isAssignableFrom(LinkedList::class.java) -> ListRandomizer.linkedListRandomizer(
                    elementRandomizer,
                    intRandomizer
                )

                klass.isAssignableFrom(ArrayList::class.java) -> ListRandomizer.arrayListRandomizer(
                    elementRandomizer,
                    intRandomizer
                )

                klass.isAssignableFrom(Stack::class.java) -> ListRandomizer.stackRandomizer(
                    elementRandomizer,
                    intRandomizer
                )

                else -> {
                    log.warn("Unknown list impl: $klass")
                    ListRandomizer.randomListRandomizer(
                        random,
                        elementRandomizer,
                        intRandomizer,
                        reflectionFacade,
                        ktRandom
                    )
                }
            }

            Queue::class.java.isAssignableFrom(klass) -> when {
                klass.isAssignableFrom(LinkedList::class.java) ->
                    QueueRandomizer.linkedQueueRandomizer(elementRandomizer, intRandomizer)

                klass.isAssignableFrom(ArrayDeque::class.java) ->
                    QueueRandomizer.arrayQueueRandomizer(elementRandomizer, intRandomizer)

                klass.isAssignableFrom(PriorityQueue::class.java) ->
                    QueueRandomizer.priorityQueueRandomizer(elementRandomizer, intRandomizer)

                else -> {
                    log.warn("Unknown queue impl: $klass")
                    QueueRandomizer.randomQueueRandomizer(
                        random,
                        elementRandomizer,
                        intRandomizer,
                        reflectionFacade,
                        ktRandom
                    )
                }
            }

            Set::class.java.isAssignableFrom(klass) -> when {
                klass.isAssignableFrom(HashSet::class.java) -> SetRandomizer.hashSetRandomizer(
                    elementRandomizer,
                    intRandomizer
                )

                klass.isAssignableFrom(LinkedHashSet::class.java) ->
                    SetRandomizer.linkedSetRandomizer(elementRandomizer, intRandomizer)

                klass.isAssignableFrom(TreeSet::class.java) -> SetRandomizer.treeSetRandomizer(
                    elementRandomizer,
                    intRandomizer
                )

                klass.isAssignableFrom(ConcurrentSkipListSet::class.java) ->
                    SetRandomizer.skipListSetRandomizer(elementRandomizer, intRandomizer)

                else -> {
                    log.warn("Unknown set impl: $klass")
                    SetRandomizer.randomSetRandomizer(
                        random,
                        elementRandomizer,
                        intRandomizer,
                        reflectionFacade,
                        ktRandom
                    )
                }
            }

            Collection::class.java.isAssignableFrom(klass) ->
                CustomCollectionRandomizer.collectionRandomizer(
                    klass,
                    random,
                    elementRandomizer,
                    intRandomizer,
                    reflectionFacade,
                    ktRandom
                )

            else -> unreachable { log.error("Unknown collection: $klass") }
        }

        fun <K, V> generateMap(
            klass: Class<*>,
            random: (Class<*>) -> Any?,
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ) = when {
            klass.isAssignableFrom(LinkedHashMap::class.java) ->
                MapRandomizer.linkedMapRandomizer(keyRandomizer, valueRandomizer, intRandomizer)

            klass.isAssignableFrom(HashMap::class.java) ->
                MapRandomizer.hashMapRandomizer(keyRandomizer, valueRandomizer, intRandomizer)

            klass.isAssignableFrom(TreeMap::class.java) ->
                MapRandomizer.treeMapRandomizer(keyRandomizer, valueRandomizer, intRandomizer)

            klass.isAssignableFrom(ConcurrentSkipListMap::class.java) ->
                MapRandomizer.skipListMapRandomizer(keyRandomizer, valueRandomizer, intRandomizer)

            Map::class.java.isAssignableFrom(klass) ->
                MapRandomizer.customMapRandomizer(
                    klass,
                    random,
                    keyRandomizer,
                    valueRandomizer,
                    intRandomizer,
                    reflectionFacade,
                    ktRandom
                )

            else -> {
                log.warn("Unknown map impl: $klass")
                MapRandomizer.randomMapRandomizer(
                    random,
                    keyRandomizer,
                    valueRandomizer,
                    intRandomizer,
                    reflectionFacade,
                    ktRandom
                )
            }
        }
    }
}


class ListRandomizer<T>(
    `impl`: () -> MutableList<T>,
    elementRandomizer: Randomizer<T>,
    intRandomizer: Randomizer<Int>
) :
    CollectionRandomizer<T>(`impl`, elementRandomizer, intRandomizer) {

    override fun getRandomValue() = super.getRandomValue() as MutableList<T>

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        fun <T> newListRandomizer(
            `impl`: () -> MutableList<T>,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>
        ) = ListRandomizer(`impl`, elementRandomizer, intRandomizer)

        fun <T> linkedListRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newListRandomizer({ LinkedList() }, elementRandomizer, intRandomizer)

        fun <T> arrayListRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newListRandomizer({ ArrayList() }, elementRandomizer, intRandomizer)

        fun <T> stackRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newListRandomizer({ Stack() }, elementRandomizer, intRandomizer)

        fun <T> randomListRandomizer(
            random: (Class<*>) -> Any?,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ): ListRandomizer<T> {
            val impl = reflectionFacade.getPublicConcreteSubTypesOf(MutableList::class.java).randomOrNull(ktRandom)
                ?: throw InstantiationError("Unable to find a matching concrete subtype of type: MutableList in the classpath")
            return newListRandomizer({
                @Suppress("UNCHECKED_CAST")
                (random(impl) as? MutableList<T>)?.also { it.clear() }
                    ?: unreachable { log.error("Could not create an instance of $impl") }
            }, elementRandomizer, intRandomizer)
        }
    }
}

class QueueRandomizer<T>(`impl`: () -> Queue<T>, elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) :
    CollectionRandomizer<T>(`impl`, elementRandomizer, intRandomizer) {

    override fun getRandomValue() = super.getRandomValue() as Queue<T>

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        fun <T> newQueueRandomizer(
            `impl`: () -> Queue<T>,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>
        ) = QueueRandomizer(`impl`, elementRandomizer, intRandomizer)

        fun <T> linkedQueueRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newQueueRandomizer({ LinkedList() }, elementRandomizer, intRandomizer)

        fun <T> arrayQueueRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newQueueRandomizer({ ArrayDeque() }, elementRandomizer, intRandomizer)

        fun <T> priorityQueueRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newQueueRandomizer({ PriorityQueue() }, elementRandomizer, intRandomizer)

        fun <T> randomQueueRandomizer(
            random: (Class<*>) -> Any?,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ): QueueRandomizer<T> {
            val impl = reflectionFacade.getPublicConcreteSubTypesOf(Queue::class.java).randomOrNull(ktRandom)
                ?: throw InstantiationError("Unable to find a matching concrete subtype of type: Queue in the classpath")
            return newQueueRandomizer({
                @Suppress("UNCHECKED_CAST")
                (random(impl) as? Queue<T>)?.also { it.clear() }
                    ?: unreachable { log.error("Could not create an instance of $impl") }
            }, elementRandomizer, intRandomizer)
        }
    }
}

class SetRandomizer<T>(`impl`: () -> MutableSet<T>, elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) :
    CollectionRandomizer<T>(`impl`, elementRandomizer, intRandomizer) {

    override fun getRandomValue() = super.getRandomValue() as MutableSet<T>

    @Suppress("unused")
    companion object {
        fun <T> newSetRandomizer(
            `impl`: () -> MutableSet<T>,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>
        ) = SetRandomizer(`impl`, elementRandomizer, intRandomizer)

        fun <T> linkedSetRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newSetRandomizer({ LinkedHashSet() }, elementRandomizer, intRandomizer)

        fun <T> hashSetRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newSetRandomizer({ HashSet() }, elementRandomizer, intRandomizer)

        fun <T> treeSetRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newSetRandomizer({ TreeSet() }, elementRandomizer, intRandomizer)

        inline fun <reified T : Enum<T>> enumSetRandomizer(
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>
        ) = newSetRandomizer({ EnumSet.noneOf(T::class.java) }, elementRandomizer, intRandomizer)

        fun <T> skipListSetRandomizer(elementRandomizer: Randomizer<T>, intRandomizer: Randomizer<Int>) =
            newSetRandomizer({ ConcurrentSkipListSet() }, elementRandomizer, intRandomizer)

        fun <T> randomSetRandomizer(
            random: (Class<*>) -> Any?,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ): SetRandomizer<T> {
            val impl = reflectionFacade.getPublicConcreteSubTypesOf(MutableSet::class.java).randomOrNull(ktRandom)
                ?: throw InstantiationError("Unable to find a matching concrete subtype of type: MutableSet in the classpath")
            return newSetRandomizer({
                @Suppress("UNCHECKED_CAST")
                (random(impl) as? MutableSet<T>)?.also { it.clear() }
                    ?: unreachable { log.error("Could not create an instance of $impl") }
            }, elementRandomizer, intRandomizer)
        }
    }
}

class MapRandomizer<K, V>(
    val collection: () -> MutableMap<K, V>,
    private val keyRandomizer: Randomizer<K>,
    private val valueRandomizer: Randomizer<V>,
    intRandomizer: Randomizer<Int>
) : Randomizer<Map<K, V>> {
    private val nbElements: Int = intRandomizer.randomValue `in` CollectionRandomizer.collectionSizeRange

    private fun getRandomKey(): K = keyRandomizer.randomValue

    private fun getRandomVal(): V = valueRandomizer.randomValue

    init {
        checkArguments(nbElements)
    }

    override fun toString(): String {
        return "MapRandomizer [impl=$collection, keys=$keyRandomizer, values=$valueRandomizer, nbElements=$nbElements]"
    }

    override fun getRandomValue(): Map<K, V> {
        val collection = collection()
        for (i in 0 until nbElements) {
            collection[getRandomKey()] = getRandomVal()
        }
        return collection
    }

    private fun checkArguments(nbElements: Int) {
        if (nbElements < 0) {
            throw IllegalArgumentException("The number of elements to generate must be >= 0")
        }
    }

    @Suppress("unused")
    companion object {
        fun <K, V> newMapRandomizer(
            `impl`: () -> MutableMap<K, V>,
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>
        ) = MapRandomizer(`impl`, keyRandomizer, valueRandomizer, intRandomizer)

        fun <K, V> linkedMapRandomizer(
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>
        ) = newMapRandomizer({ LinkedHashMap() }, keyRandomizer, valueRandomizer, intRandomizer)

        fun <K, V> hashMapRandomizer(
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>
        ) = newMapRandomizer({ HashMap() }, keyRandomizer, valueRandomizer, intRandomizer)

        fun <K, V> treeMapRandomizer(
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>
        ) = newMapRandomizer({ TreeMap() }, keyRandomizer, valueRandomizer, intRandomizer)

        inline fun <reified K : Enum<K>, V> enumMapRandomizer(
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>
        ) = newMapRandomizer({ EnumMap(K::class.java) }, keyRandomizer, valueRandomizer, intRandomizer)

        fun <K, V> skipListMapRandomizer(
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>
        ) = newMapRandomizer({ ConcurrentSkipListMap() }, keyRandomizer, valueRandomizer, intRandomizer)

        fun <K, V> randomMapRandomizer(
            random: (Class<*>) -> Any?,
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ): MapRandomizer<K, V> {
            val impl = reflectionFacade.getPublicConcreteSubTypesOf(MutableMap::class.java).randomOrNull(ktRandom)
                ?: throw InstantiationError("Unable to find a matching concrete subtype of type: MutableMap in the classpath")
            return newMapRandomizer({
                @Suppress("UNCHECKED_CAST")
                random(impl) as? MutableMap<K, V>
                    ?: unreachable { log.error("Could not create an instance of $impl") }
            }, keyRandomizer, valueRandomizer, intRandomizer)
        }

        fun <K, V> customMapRandomizer(
            klass: Class<*>, random: (Class<*>) -> Any?,
            keyRandomizer: Randomizer<K>,
            valueRandomizer: Randomizer<V>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ): MapRandomizer<K, V> {
            val actualKlass = when {
                klass.isAbstract || klass.isInterface -> reflectionFacade.getPublicConcreteSubTypesOf(klass)
                    .randomOrNull(ktRandom)
                    ?: throw InstantiationError("Unable to find a matching concrete subtype of type: $klass in the classpath")

                else -> klass
            }
            return newMapRandomizer({
                @Suppress("UNCHECKED_CAST")
                random(actualKlass) as? MutableMap<K, V>
                    ?: unreachable { log.error("Could not create an instance of $klass") }
            }, keyRandomizer, valueRandomizer, intRandomizer)
        }

    }
}

class CustomCollectionRandomizer<T>(
    `impl`: () -> MutableCollection<T>,
    delegate: Randomizer<T>,
    intRandomizer: Randomizer<Int>
) : CollectionRandomizer<T>(`impl`, delegate, intRandomizer) {

    companion object {
        fun <T> collectionRandomizer(
            klass: Class<*>,
            random: (Class<*>) -> Any?,
            elementRandomizer: Randomizer<T>,
            intRandomizer: Randomizer<Int>,
            reflectionFacade: ReflectionFacade,
            ktRandom: kotlin.random.Random
        ): CollectionRandomizer<T> {
            val actualKlass = when {
                klass.isAbstract || klass.isInterface -> reflectionFacade.getPublicConcreteSubTypesOf(klass)
                    .randomOrNull(ktRandom)
                    ?: throw InstantiationError("Unable to find a matching concrete subtype of type: $klass in the classpath")

                else -> klass
            }
            return CustomCollectionRandomizer(
                {
                    @Suppress("UNCHECKED_CAST")
                    random(actualKlass) as? MutableCollection<T>
                        ?: unreachable { log.error("Could not create an instance of $klass") }
                },
                elementRandomizer,
                intRandomizer
            )
        }
    }
}
