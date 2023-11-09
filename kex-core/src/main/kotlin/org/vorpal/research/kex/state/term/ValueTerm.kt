package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ValueTerm(override val type: KexType, override val name: String) : Term() {
    override val subTerms by lazy { emptyList<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}