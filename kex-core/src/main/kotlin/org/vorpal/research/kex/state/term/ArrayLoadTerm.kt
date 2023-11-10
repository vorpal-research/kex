package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayLoadTerm(override val type: KexType, val arrayRef: Term) : Term() {
    override val name = "*($arrayRef)"
    override val subTerms by lazy { listOf(arrayRef) }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term =
            when (val tArrayRef = t.transform(arrayRef)) {
                arrayRef -> this
                else -> term { termFactory.getArrayLoad(type, tArrayRef) }
            }
}
