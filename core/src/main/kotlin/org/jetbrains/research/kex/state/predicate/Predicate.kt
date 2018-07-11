package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.Sealed
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kex.util.defaultHashCode

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
}

abstract class Predicate(val type: PredicateType, val operands: List<Term>) : Sealed, Loggable {
    companion object {
        val predicates = mapOf<String, Class<*>>(
                "ArrayStore" to ArrayStorePredicate::class.java,
                "Call" to CallPredicate::class.java,
                "Catch" to CatchPredicate::class.java,
                "DefaultSwitch" to DefaultSwitchPredicate::class.java,
                "Equality" to EqualityPredicate::class.java,
                "FieldStore" to FieldStorePredicate::class.java,
                "NewArray" to NewArrayPredicate::class.java,
                "New" to NewPredicate::class.java,
                "Throw" to ThrowPredicate::class.java
        )
        val reverse = predicates.map { it.value to it.key }.toMap()
    }

    fun getNumOperands() = operands.size

    abstract fun print(): String
    abstract fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate

    override fun hashCode() = defaultHashCode(type, *operands.toTypedArray())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Predicate
        return this.type == other.type && this.operands.contentEquals(other.operands)
    }

    override fun getSubtypes() = predicates
    override fun getReverseMapping() = reverse
    override fun toString() = "$type ${print()}"
}