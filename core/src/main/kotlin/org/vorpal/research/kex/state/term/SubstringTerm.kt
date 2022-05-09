package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class SubstringTerm(
    override val type: KexType,
    val string: Term,
    val offset: Term,
    val length: Term
) : Term() {
    override val name = "${string}.substring($offset, $length)"
    override val subTerms by lazy { listOf(string, offset, length) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tString = t.transform(string)
        val tOffset = t.transform(offset)
        val tLength = t.transform(length)
        return when {
            tString == string && tOffset == offset && tLength == length -> this
            else -> term { tString.substring(tOffset, tLength) }
        }
    }
}