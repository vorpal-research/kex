package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class StringLengthTerm(
    val string: Term
) : Term() {
    override val type = KexInt
    override val name = "$string.length"
    override val subTerms by lazy { listOf(string) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
        when (val tString = t.transform(string)) {
            string -> this
            else -> term { tString.length() }
        }
}
