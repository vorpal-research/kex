package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ArrayLengthTerm(type: KexType, arrayRef: Term) : Term("", type, listOf(arrayRef)) {

    val arrayRef: Term
        get() = subterms[0]

    override fun print() = "$arrayRef.length"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tarrayRef = t.transform(arrayRef)
        return when (tarrayRef) {
            arrayRef -> this
            else -> t.tf.getArrayLength(tarrayRef)
        }
    }
}