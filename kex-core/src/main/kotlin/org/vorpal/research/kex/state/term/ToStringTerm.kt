package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ToStringTerm(
    override val type: KexType,
    val value: Term
) : Term() {
    override val name = "${value.type}.toString($value)"
    override val subTerms by lazy { listOf(value) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
        when (val tValue = t.transform(value)) {
            value -> this
            else -> term { termFactory.getArrayLength(tValue) }
        }
}
