package org.jetbrains.research.kex.random.easyrandom

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jeasy.random.api.Randomizer
import org.jeasy.random.randomizers.number.ByteRandomizer.aNewByteRandomizer
import org.jeasy.random.util.CollectionUtils.randomElementOf
import org.jeasy.random.util.ReflectionUtils
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.util.isAbstract
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet
import kotlin.math.abs

private infix fun Int.`in`(range: IntRange) = range.first + (this % (range.last - range.first))

private inline val randomInt get() = abs(aNewByteRandomizer().randomValue!!.toInt())

abstract class CollectionRandomizer<T>(
        val collection: () -> MutableCollection<T>,
        private val delegate: Randomizer<T>,
        private val nbElements: Int = randomInt `in` collectionSizeRange
) : Randomizer<Collection<T>> {

    val randomElement: T
        get() = delegate.randomValue

    override fun toString(): String {
        return "CollectionRandomizer [impl=$collection, delegate=$delegate, nbElements=$nbElements]"
    }

    override fun getRandomValue(): MutableCollection<T> {
        val collection = collection()
        for (i in 0 until nbElements) {
            collection.add(randomElement)
        }
        return collection
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        val collectionSizeRange: IntRange by lazy {
            val minCollectionSize = kexConfig.getIntValue("easy-random", "minCollectionSize", 0)
            val maxCollectionSize = kexConfig.getIntValue("easy-random", "maxCollectionSize", 1000)
            minCollectionSize..maxCollectionSize
        }

        fun <T> generateCollection(klass: Class<*>,
                                   random: (Class<*>) -> Any?,
                                   elementRandomizer: Randomizer<T>) = when {
            List::class.java.isAssignableFrom(klass) -> when {
                klass.isAssignableFrom(LinkedList::class.java) -> ListRandomizer.linkedListRandomizer(elementRandomizer)
                klass.isAssignableFrom(ArrayList::class.java) -> ListRandomizer.arrayListRandomizer(elementRandomizer)
                klass.isAssignableFrom(Stack::class.java) -> ListRandomizer.stackRandomizer(elementRandomizer)
                else -> {
                    log.warn("Unknown list impl: $klass")
                    ListRandomizer.randomListRandomizer(random, elementRandomizer)
                }
            }
            Queue::class.java.isAssignableFrom(klass) -> when {
                klass.isAssignableFrom(LinkedList::class.java) ->
                    QueueRandomizer.linkedQueueRandomizer(elementRandomizer)
                klass.isAssignableFrom(ArrayDeque::class.java) ->
                    QueueRandomizer.arrayQueueRandomizer(elementRandomizer)
                klass.isAssignableFrom(PriorityQueue::class.java) ->
                    QueueRandomizer.priorityQueueRandomizer(elementRandomizer)
                else -> {
                    log.warn("Unknown queue impl: $klass")
                    QueueRandomizer.randomQueueRandomizer(random, elementRandomizer)
                }
            }
            Set::class.java.isAssignableFrom(klass) -> when {
                klass.isAssignableFrom(LinkedHashSet::class.java) ->
                    SetRandomizer.linkedSetRandomizer(elementRandomizer)
                klass.isAssignableFrom(HashSet::class.java) -> SetRandomizer.hashSetRandomizer(elementRandomizer)
                klass.isAssignableFrom(TreeSet::class.java) -> SetRandomizer.treeSetRandomizer(elementRandomizer)
                klass.isAssignableFrom(ConcurrentSkipListSet::class.java) ->
                    SetRandomizer.skipListSetRandomizer(elementRandomizer)
                else -> {
                    log.warn("Unknown set impl: $klass")
                    SetRandomizer.randomSetRandomizer(random, elementRandomizer)
                }
            }
            Collection::class.java.isAssignableFrom(klass) ->
                CustomCollectionRandomizer.collectionRandomizer(klass, random, elementRandomizer)
            else -> unreachable { log.error("Unknown collection: $klass") }
        }

        fun <K, V> generateMap(klass: Class<*>,
                               random: (Class<*>) -> Any?,
                               keyRandomizer: Randomizer<K>,
                               valueRandomizer: Randomizer<V>) = when {
            klass.isAssignableFrom(LinkedHashMap::class.java) ->
                MapRandomizer.linkedMapRandomizer(keyRandomizer, valueRandomizer)
            klass.isAssignableFrom(HashMap::class.java) ->
                MapRandomizer.hashMapRandomizer(keyRandomizer, valueRandomizer)
            klass.isAssignableFrom(TreeMap::class.java) ->
                MapRandomizer.treeMapRandomizer(keyRandomizer, valueRandomizer)
            klass.isAssignableFrom(ConcurrentSkipListMap::class.java) ->
                MapRandomizer.skipListMapRandomizer(keyRandomizer, valueRandomizer)
            Map::class.java.isAssignableFrom(klass) ->
                MapRandomizer.customMapRandomizer(klass, random, keyRandomizer, valueRandomizer)
            else -> {
                log.warn("Unknown map impl: $klass")
                MapRandomizer.randomMapRandomizer(random, keyRandomizer, valueRandomizer)
            }
        }
    }
}


class ListRandomizer<T>(`impl`: () -> MutableList<T>, elementRandomizer: Randomizer<T>, nbElements: Int)
    : CollectionRandomizer<T>(`impl`, elementRandomizer, nbElements) {

    constructor(`impl`: () -> MutableList<T>, elementRandomizer: Randomizer<T>)
            : this(`impl`, elementRandomizer, randomInt)

    override fun getRandomValue() = super.getRandomValue() as MutableList<T>

    companion object {
        fun <T> newListRandomizer(`impl`: () -> MutableList<T>, elementRandomizer: Randomizer<T>) =
                ListRandomizer(`impl`, elementRandomizer)

        fun <T> newListRandomizer(`impl`: () -> MutableList<T>, elementRandomizer: Randomizer<T>, nbElements: Int) =
                ListRandomizer(`impl`, elementRandomizer, nbElements)

        fun <T> linkedListRandomizer(elementRandomizer: Randomizer<T>) =
                newListRandomizer({ LinkedList() }, elementRandomizer)

        fun <T> arrayListRandomizer(elementRandomizer: Randomizer<T>) =
                newListRandomizer({ ArrayList() }, elementRandomizer)

        fun <T> stackRandomizer(elementRandomizer: Randomizer<T>) =
                newListRandomizer({ Stack() }, elementRandomizer)

        fun <T> randomListRandomizer(random: (Class<*>) -> Any?, elementRandomizer: Randomizer<T>): ListRandomizer<T> {
            val impl = randomElementOf<Class<*>>(ReflectionUtils.getPublicConcreteSubTypesOf(MutableList::class.java))
            return newListRandomizer({
                @Suppress("UNCHECKED_CAST")
                random(impl) as? MutableList<T>
                        ?: unreachable { log.error("Could not create an instance of $impl") }
            }, elementRandomizer)
        }
    }
}

class QueueRandomizer<T>(`impl`: () -> Queue<T>, elementRandomizer: Randomizer<T>, nbElements: Int)
    : CollectionRandomizer<T>(`impl`, elementRandomizer, nbElements) {

    constructor(`impl`: () -> Queue<T>, elementRandomizer: Randomizer<T>)
            : this(`impl`, elementRandomizer, randomInt)

    override fun getRandomValue() = super.getRandomValue() as Queue<T>

    companion object {
        fun <T> newQueueRandomizer(`impl`: () -> Queue<T>, elementRandomizer: Randomizer<T>) =
                QueueRandomizer(`impl`, elementRandomizer)

        fun <T> newQueueRandomizer(`impl`: () -> Queue<T>, elementRandomizer: Randomizer<T>, nbElements: Int) =
                QueueRandomizer(`impl`, elementRandomizer, nbElements)

        fun <T> linkedQueueRandomizer(elementRandomizer: Randomizer<T>) =
                newQueueRandomizer({ LinkedList() }, elementRandomizer)

        fun <T> arrayQueueRandomizer(elementRandomizer: Randomizer<T>) =
                newQueueRandomizer({ ArrayDeque() }, elementRandomizer)

        fun <T> priorityQueueRandomizer(elementRandomizer: Randomizer<T>) =
                newQueueRandomizer({ PriorityQueue() }, elementRandomizer)

        fun <T> randomQueueRandomizer(random: (Class<*>) -> Any?, elementRandomizer: Randomizer<T>): QueueRandomizer<T> {
            val impl = randomElementOf<Class<*>>(ReflectionUtils.getPublicConcreteSubTypesOf(Queue::class.java))
            return newQueueRandomizer({
                @Suppress("UNCHECKED_CAST")
                random(impl) as? Queue<T>
                        ?: unreachable { log.error("Could not create an instance of $impl") }
            }, elementRandomizer)
        }
    }
}

class SetRandomizer<T>(`impl`: () -> MutableSet<T>, elementRandomizer: Randomizer<T>, nbElements: Int)
    : CollectionRandomizer<T>(`impl`, elementRandomizer, nbElements) {

    constructor(`impl`: () -> MutableSet<T>, elementRandomizer: Randomizer<T>)
            : this(`impl`, elementRandomizer, randomInt)

    override fun getRandomValue() = super.getRandomValue() as MutableSet<T>

    companion object {
        fun <T> newSetRandomizer(`impl`: () -> MutableSet<T>, elementRandomizer: Randomizer<T>) =
                SetRandomizer(`impl`, elementRandomizer)

        fun <T> newSetRandomizer(`impl`: () -> MutableSet<T>, elementRandomizer: Randomizer<T>, nbElements: Int) =
                SetRandomizer(`impl`, elementRandomizer, nbElements)

        fun <T> linkedSetRandomizer(elementRandomizer: Randomizer<T>) =
                newSetRandomizer({ LinkedHashSet() }, elementRandomizer)

        fun <T> hashSetRandomizer(elementRandomizer: Randomizer<T>) =
                newSetRandomizer({ HashSet() }, elementRandomizer)

        fun <T> treeSetRandomizer(elementRandomizer: Randomizer<T>) =
                newSetRandomizer({ TreeSet() }, elementRandomizer)

        inline fun <reified T : Enum<T>> enumSetRandomizer(elementRandomizer: Randomizer<T>) =
                newSetRandomizer({ EnumSet.noneOf(T::class.java) }, elementRandomizer)

        fun <T> skipListSetRandomizer(elementRandomizer: Randomizer<T>) =
                newSetRandomizer({ ConcurrentSkipListSet() }, elementRandomizer)

        fun <T> randomSetRandomizer(random: (Class<*>) -> Any?, elementRandomizer: Randomizer<T>): SetRandomizer<T> {
            val impl = randomElementOf<Class<*>>(ReflectionUtils.getPublicConcreteSubTypesOf(MutableSet::class.java))
            return newSetRandomizer({
                @Suppress("UNCHECKED_CAST")
                random(impl) as? MutableSet<T>
                        ?: unreachable { log.error("Could not create an instance of $impl") }
            }, elementRandomizer)
        }
    }
}

class MapRandomizer<K, V>(
        val collection: () -> MutableMap<K, V>,
        val keyRandomizer: Randomizer<K>,
        val valueRandomizer: Randomizer<V>,
        val nbElements: Int = randomInt
) : Randomizer<Map<K, V>> {

    val randomKey: K
        get() = keyRandomizer.randomValue

    val randomVal: V
        get() = valueRandomizer.randomValue

    init {
        checkArguments(nbElements)
    }

    override fun toString(): String {
        return "MapRandomizer [impl=$collection, keys=$keyRandomizer, values=$valueRandomizer, nbElements=$nbElements]"
    }

    override fun getRandomValue(): Map<K, V> {
        val collection = collection()
        for (i in 0 until nbElements) {
            collection[randomKey] = randomVal
        }
        return collection
    }

    private fun checkArguments(nbElements: Int) {
        if (nbElements < 0) {
            throw IllegalArgumentException("The number of elements to generate must be >= 0")
        }
    }

    companion object {
        fun <K, V> newMapRandomizer(`impl`: () -> MutableMap<K, V>,
                                    keyRandomizer: Randomizer<K>,
                                    valueRandomizer: Randomizer<V>) =
                MapRandomizer(`impl`, keyRandomizer, valueRandomizer)

        fun <K, V> newMapRandomizer(`impl`: () -> MutableMap<K, V>,
                                    keyRandomizer: Randomizer<K>,
                                    valueRandomizer: Randomizer<V>,
                                    nbElements: Int) =
                MapRandomizer(`impl`, keyRandomizer, valueRandomizer, nbElements)

        fun <K, V> linkedMapRandomizer(keyRandomizer: Randomizer<K>, valueRandomizer: Randomizer<V>) =
                newMapRandomizer({ LinkedHashMap() }, keyRandomizer, valueRandomizer)

        fun <K, V> hashMapRandomizer(keyRandomizer: Randomizer<K>, valueRandomizer: Randomizer<V>) =
                newMapRandomizer({ HashMap() }, keyRandomizer, valueRandomizer)

        fun <K, V> treeMapRandomizer(keyRandomizer: Randomizer<K>, valueRandomizer: Randomizer<V>) =
                newMapRandomizer({ TreeMap() }, keyRandomizer, valueRandomizer)

        inline fun <reified K : Enum<K>, V> enumMapRandomizer(keyRandomizer: Randomizer<K>, valueRandomizer: Randomizer<V>) =
                newMapRandomizer({ EnumMap(K::class.java) }, keyRandomizer, valueRandomizer)

        fun <K, V> skipListMapRandomizer(keyRandomizer: Randomizer<K>, valueRandomizer: Randomizer<V>) =
                newMapRandomizer({ ConcurrentSkipListMap() }, keyRandomizer, valueRandomizer)

        fun <K, V> randomMapRandomizer(random: (Class<*>) -> Any?,
                                       keyRandomizer: Randomizer<K>,
                                       valueRandomizer: Randomizer<V>): MapRandomizer<K, V> {
            val impl = randomElementOf<Class<*>>(ReflectionUtils.getPublicConcreteSubTypesOf(MutableMap::class.java))
            return newMapRandomizer({
                @Suppress("UNCHECKED_CAST")
                random(impl) as? MutableMap<K, V>
                        ?: unreachable { log.error("Could not create an instance of $impl") }
            }, keyRandomizer, valueRandomizer)
        }

        fun <K, V> customMapRandomizer(klass: Class<*>, random: (Class<*>) -> Any?,
                                       keyRandomizer: Randomizer<K>,
                                       valueRandomizer: Randomizer<V>): MapRandomizer<K, V> {
            val actualKlass = when {
                klass.isAbstract || klass.isInterface -> randomElementOf<Class<*>>(ReflectionUtils.getPublicConcreteSubTypesOf(klass))
                else -> klass
            }
            return newMapRandomizer({
                @Suppress("UNCHECKED_CAST")
                random(actualKlass) as? MutableMap<K, V>
                        ?: unreachable { log.error("Could not create an instance of $klass") }
            }, keyRandomizer, valueRandomizer)
        }

    }
}

class CustomCollectionRandomizer<T>(`impl`: () -> MutableCollection<T>, delegate: Randomizer<T>, nbElements: Int)
    : CollectionRandomizer<T>(`impl`, delegate, nbElements) {
    constructor(`impl`: () -> MutableCollection<T>, delegate: Randomizer<T>) : this(impl, delegate, randomInt)

    companion object {
        fun <T> collectionRandomizer(klass: Class<*>, random: (Class<*>) -> Any?, elementRandomizer: Randomizer<T>): CollectionRandomizer<T> {
            val actualKlass = when {
                klass.isAbstract || klass.isInterface -> randomElementOf<Class<*>>(ReflectionUtils.getPublicConcreteSubTypesOf(klass))
                else -> klass
            }
            return CustomCollectionRandomizer(
                    {
                        @Suppress("UNCHECKED_CAST")
                        random(actualKlass) as? MutableCollection<T>
                                ?: unreachable { log.error("Could not create an instance of $klass") }
                    }, elementRandomizer)
        }
    }
}