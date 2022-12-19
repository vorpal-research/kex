package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class IndexOfTerm(
    val string: Term,
    val substring: Term,
    val offset: Term
) : Term() {
    override val type = KexInt
    override val name = "${string}.indexOf($substring, $offset)"
    override val subTerms by lazy { listOf(string, substring, offset) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tString = t.transform(string)
        val tOffset = t.transform(offset)
        val tLength = t.transform(offset)
        return when {
            tString == string && tOffset == offset && tLength == offset -> this
            else -> term { tString.indexOf(tOffset, tLength) }
        }
    }
}
