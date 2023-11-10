package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayLengthTerm(val arrayRef: Term) : Term() {
    override val type = KexInt
    override val name = "$arrayRef.length"
    override val subTerms by lazy { listOf(arrayRef) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
            when (val tArrayRef = t.transform(arrayRef)) {
                arrayRef -> this
                else -> term { termFactory.getArrayLength(tArrayRef) }
            }
}
