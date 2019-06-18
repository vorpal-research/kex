package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexNull
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class NullTerm : Term() {
    override val name = "null"
    override val type = KexNull()
    override val subterms: List<Term>
        get() = listOf()

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}