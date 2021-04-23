package org.jetbrains.research.kex.reanimator.descriptor

import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.reanimator.NoConcreteInstanceException
import org.jetbrains.research.kfg.ir.Class

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }

object ConcreteInstanceGenerator {
    private val concreteInstanceInfo = mutableMapOf(
            "java/util/Collection" to setOf("java/util/ArrayList", "java/util/LinkedList"),
            "java/util/List" to setOf("java/util/ArrayList", "java/util/LinkedList"),
            "java/util/Queue" to setOf("java/util/ArrayList", "java/util/LinkedList"),
            "java/util/Deque" to setOf("java/util/ArrayDeque", "java/util/LinkedList"),
            "java/util/Set" to setOf("java/util/TreeSet", "java/util/HashSet"),
            "java/util/SortedSet" to setOf("java/util/TreeSet"),
            "java/util/NavigableSet" to setOf("java/util/TreeSet"),
            "java/util/Map" to setOf("java/util/TreeMap", "java/util/HashMap"),
            "java/util/SortedMap" to setOf("java/util/TreeMap"),
            "java/util/NavigableMap" to setOf("java/util/TreeMap")
    )

    operator fun get(klass: Class) = `try` {
        val newKlass = concreteInstanceInfo.getOrElse(klass.fullname) {
            klass.cm.concreteClasses.filter {
                klass.isAncestorOf(it) && it.isInstantiable && visibilityLevel <= it.visibility
            }.map {
                it.fullname
            }
        }.random()
        klass.cm[newKlass]
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }
}