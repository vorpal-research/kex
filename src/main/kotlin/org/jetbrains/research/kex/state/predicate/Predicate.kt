package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.util.defaultHashCode

sealed class PredicateType(val name: String) {
    override fun toString(): String {
        return "@$name"
    }

    class Path() : PredicateType("P")
    class State() : PredicateType("S")
}

abstract class Predicate(val type: PredicateType, protected val operands: Array<Term>) {
    abstract fun <T> accept(t: Transformer<T>): Predicate

    abstract fun print(): String
    override fun toString() = "$type ${print()}"

    fun getNumOperands() = operands.size

    override fun hashCode() = defaultHashCode(type, *operands)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Predicate
        return this.type == other.type && this.operands.contentEquals(other.operands)
    }
}