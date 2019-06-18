package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexFloat
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ConstFloatTerm(val value: Float) : Term() {
    override val name = value.toString()
    override val type: KexType = KexFloat()
    override val subterms: List<Term>
        get() = listOf()

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}