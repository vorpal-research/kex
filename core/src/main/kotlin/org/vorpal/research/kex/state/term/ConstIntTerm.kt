package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ConstIntTerm(val value: Int): Term() {
    override val name = value.toString()
    override val type: KexType = KexInt
    override val subTerms by lazy { listOf<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}
