package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode

class ArgumentTerm(type: KexType, val index: Int) : Term("arg$$index", type, listOf()) {
    override fun print() = name

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this

    override fun hashCode() = defaultHashCode(index, super.hashCode())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ArgumentTerm
        return this.index == other.index && super.equals(other)
    }
}