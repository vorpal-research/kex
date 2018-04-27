package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.defaultHashCode

abstract class Term(val name: String, val type: Type, val subterms: Array<Term>) {
    override fun hashCode() = defaultHashCode(name, type, *subterms)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Term
        return this.name == other.name && this.type == other.type && this.subterms.contentEquals(other.subterms)
    }

    override fun toString() = print()

    abstract fun print(): String
}