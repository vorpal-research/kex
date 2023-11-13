package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class CharAtTerm(
    val string: Term,
    val index: Term
) : Term() {
    override val type = KexChar
    override val name = "$string[$index]"
    override val subTerms by lazy { listOf(string, index) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tString = t.transform(string)
        val tIndex = t.transform(index)
        return when {
            tString == string && tIndex == index -> this
            else -> term { tString.charAt(tIndex) }
        }
    }
}
