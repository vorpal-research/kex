package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.BaseType
import org.jetbrains.research.kex.state.InheritanceInfo
import org.jetbrains.research.kex.state.TypeInfo
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.ir.Location

sealed class PredicateType(val name: String) {
    override fun toString(): String {
        return "@$name"
    }

    class Path : PredicateType("P") {
        override fun hashCode() = defaultHashCode(name)
        override fun equals(other: Any?) = when {
            this === other -> true
            other == null -> false
            other is Path -> true
            else -> false
        }
    }

    class State : PredicateType("S") {
        override fun hashCode() = defaultHashCode(name)
        override fun equals(other: Any?) = when {
            this === other -> true
            other == null -> false
            other is State -> true
            else -> false
        }
    }

    class Assume : PredicateType("A") {
        override fun hashCode() = defaultHashCode(name)
        override fun equals(other: Any?) = when {
            this === other -> true
            other == null -> false
            other is State -> true
            else -> false
        }
    }
}

@BaseType("Predicate")
abstract class Predicate(val type: PredicateType, val location: Location, val operands: List<Term>) : TypeInfo {
    companion object {
        val predicates = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("Predicate.json")
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo?.inheritors?.map {
                it.name to loader.loadClass(it.inheritorClass)
            }?.toMap() ?: mapOf()
        }

        val reverse = predicates.map { it.value to it.key }.toMap()
    }

    val size: Int
        get() = operands.size

    abstract fun print(): String
    abstract fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate

    override fun hashCode() = defaultHashCode(type, *operands.toTypedArray())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Predicate
        return this.type == other.type && this.operands.contentEquals(other.operands)
    }

    override val inheritors get() = predicates
    override val reverseMapping get() = reverse
    override fun toString() = "$type ${print()}"
}

val Predicate.hasReceiver
    get() = when (this) {
        is DefaultSwitchPredicate -> true
        is EqualityPredicate -> true
        is NewArrayPredicate -> true
        is InequalityPredicate -> true
        is NewPredicate -> true
        is ArrayStorePredicate -> true
        is FieldStorePredicate -> true
        else -> false
    }

val Predicate.receiver get() = if (hasReceiver) operands[0] else null