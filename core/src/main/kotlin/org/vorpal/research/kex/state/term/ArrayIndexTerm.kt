package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayIndexTerm(override val type: KexType, val arrayRef: Term, val index: Term) : Term() {
    override val name = "$arrayRef[$index]"
    override val subTerms by lazy { listOf(arrayRef, index) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tArrayRef = t.transform(arrayRef)
        val tIndex = t.transform(index)
        return when {
            tArrayRef == arrayRef && tIndex == index -> this
            else -> term { termFactory.getArrayIndex(type, tArrayRef, tIndex) }
        }
    }

}
