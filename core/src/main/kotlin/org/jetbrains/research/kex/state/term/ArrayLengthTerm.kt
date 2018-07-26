package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class ArrayLengthTerm(type: Type, arrayRef: Term) : Term("", type, listOf(arrayRef)) {

    val arrayRef: Term
        get() = subterms[0]

    override fun print() = "$arrayRef.length"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tarrayRef = t.transform(arrayRef)
        return when {
            tarrayRef == arrayRef -> this
            else -> t.tf.getArrayLength(tarrayRef)
        }
    }
}