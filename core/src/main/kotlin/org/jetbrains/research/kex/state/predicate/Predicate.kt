package org.jetbrains.research.kex.state.predicate

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

abstract class Predicate(val type: PredicateType, val location: Location, val operands: List<Term>) : TypeInfo {
    companion object {
        val predicates = mapOf<String, Class<*>>(
                "ArrayStore" to ArrayStorePredicate::class.java,
                "BoundStore" to BoundStorePredicate::class.java,
                "Call" to CallPredicate::class.java,
                "Catch" to CatchPredicate::class.java,
                "DefaultSwitch" to DefaultSwitchPredicate::class.java,
                "Inequality" to InequalityPredicate::class.java,
                "Equality" to EqualityPredicate::class.java,
                "FieldStore" to FieldStorePredicate::class.java,
                "NewArray" to NewArrayPredicate::class.java,
                "New" to NewPredicate::class.java,
                "Throw" to ThrowPredicate::class.java
        )
        val reverse = predicates.map { it.value to it.key }.toMap()

        fun getReciever(predicate: Predicate): Term? = when (predicate) {
            is DefaultSwitchPredicate, is EqualityPredicate, is NewArrayPredicate,
            is NewPredicate, is ArrayStorePredicate, is FieldStorePredicate -> predicate.operands[0]
            else -> null
        }
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

    override val subtypes get() = predicates
    override val reverseMapping get() = reverse
    override fun toString() = "$type ${print()}"
}