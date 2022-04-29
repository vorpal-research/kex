package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kex.util.kapitalize

@InheritorOf("Term")
@Serializable
class StringParseTerm(
    override val type: KexType,
    val string: Term,
) : Term() {
    override val name = "parse${type.toString().kapitalize()}($string)"
    override val subTerms by lazy { listOf(string) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
        when (val tString = t.transform(string)) {
            string -> this
            else -> term { type.fromString(tString) }
        }
}