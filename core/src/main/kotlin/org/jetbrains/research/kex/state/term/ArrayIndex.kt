package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayIndexTerm(override val type: KexType, val arrayRef: Term, val index: Term) : Term() {
    override val name = "$arrayRef[$index]"
    override val subterms: List<Term>
        get() = listOf(arrayRef, index)

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tarrayRef = t.transform(arrayRef)
        val tindex = t.transform(index)
        return when {
            tarrayRef == arrayRef && tindex == index -> this
            else -> t.tf.getArrayIndex(type, tarrayRef, tindex)
        }
    }

}