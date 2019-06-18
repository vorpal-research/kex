package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexChar
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ConstCharTerm(val value: Char) : Term() {
    override val name = value.toString()
    override val type: KexType = KexChar()
    override val subterms: List<Term>
        get() = listOf()

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}