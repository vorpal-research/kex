package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class StartsWithTerm(
    val string: Term,
    val prefix: Term
) : Term() {
    override val type = KexBool()
    override val name = "$string.startsWith($prefix)"
    override val subTerms by lazy { listOf(string, prefix) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tString = t.transform(string)
        val tPrefix = t.transform(prefix)
        return when {
            tString == string && tPrefix == prefix -> this
            else -> term { tString.startsWith(tPrefix) }
        }
    }
}