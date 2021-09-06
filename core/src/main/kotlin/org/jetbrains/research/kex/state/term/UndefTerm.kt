package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class UndefTerm(override val type: KexType) : Term() {
    override val name = "<undef>"
    override val subTerms by lazy { listOf<Term>() }

    override fun <T : Transformer<T>> accept(t: Transformer<T>) = this

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return this === other
    }
}