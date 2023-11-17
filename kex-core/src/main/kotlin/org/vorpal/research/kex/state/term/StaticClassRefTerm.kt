package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class StaticClassRefTerm(
    override val type: KexType
) : Term() {
    override val name: String = type.toString()
    override val subTerms = emptyList<Term>()

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term = this
}