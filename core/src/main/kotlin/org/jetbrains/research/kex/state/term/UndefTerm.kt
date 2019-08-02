package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class UndefTerm(override val type: KexType) : Term() {
    override val name = "<undef>"
    override val subterms by lazy { listOf<Term>() }

    override fun <T : Transformer<T>> accept(t: Transformer<T>) = this

    override fun equals(other: Any?): Boolean {
        return this === other
    }
}