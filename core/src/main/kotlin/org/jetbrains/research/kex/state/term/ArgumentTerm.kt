package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kthelper.defaultHashCode
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArgumentTerm(override val type: KexType, val index: Int) : Term() {
    override val name = "arg$$index"
    override val subterms by lazy { listOf<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this

    override fun hashCode() = defaultHashCode(index, super.hashCode())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ArgumentTerm
        return this.index == other.index && super.equals(other)
    }
}