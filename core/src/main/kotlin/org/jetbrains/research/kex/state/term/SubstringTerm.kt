package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexString
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class SubstringTerm(
    val string: Term,
    val offset: Term,
    val length: Term
) : Term() {
    override val type = KexString()
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