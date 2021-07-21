package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class StringLengthTerm(
    val string: Term
) : Term() {
    override val type = KexInt()
    override val name = "$string.length"
    override val subTerms by lazy { listOf(string) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
        when (val tString = t.transform(string)) {
            string -> this
            else -> term { tString.length() }
        }
}