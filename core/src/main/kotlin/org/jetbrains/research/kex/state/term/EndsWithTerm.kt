package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class EndsWithTerm(
    val string: Term,
    val suffix: Term
) : Term() {
    override val type = KexBool()
    override val name = "$string.endsWith($suffix)"
    override val subTerms by lazy { listOf(string, suffix) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tString = t.transform(string)
        val tSuffix = t.transform(suffix)
        return when {
            tString == string && tSuffix == suffix -> this
            else -> term { tString.endsWith(tSuffix) }
        }
    }
}