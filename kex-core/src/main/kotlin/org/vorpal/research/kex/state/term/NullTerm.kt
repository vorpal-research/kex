package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class NullTerm : Term() {
    override val name = "null"
    override val type = KexNull()
    override val subTerms by lazy { listOf<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}