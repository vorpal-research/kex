package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ConstCharTerm(val value: Char) : Term() {
    override val name = value.toString()
    override val type: KexType = KexChar
    override val subTerms by lazy { listOf<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}
