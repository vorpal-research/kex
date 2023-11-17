package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class StringContainsTerm(
    val string: Term,
    val substring: Term
) : Term() {
    override val type = KexBool
    override val name = "$substring in $string"
    override val subTerms by lazy { listOf(string, substring) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tString = t.transform(string)
        val tSubstring = t.transform(substring)
        return when {
            tString == string && tSubstring == substring -> this
            else -> term { tSubstring `in` tString }
        }
    }
}
