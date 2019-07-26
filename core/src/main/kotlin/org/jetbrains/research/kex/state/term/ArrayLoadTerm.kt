package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayLoadTerm(override val type: KexType, val arrayRef: Term) : Term() {
    override val name = "*($arrayRef)"
    override val subterms by lazy { listOf(arrayRef) }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tarrayRef = t.transform(arrayRef)
        return when (tarrayRef) {
            arrayRef -> this
            else -> t.tf.getArrayLoad(type, tarrayRef)
        }
    }
}