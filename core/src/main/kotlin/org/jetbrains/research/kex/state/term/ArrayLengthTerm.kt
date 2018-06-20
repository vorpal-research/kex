package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF

class ArrayLengthTerm(arrayRef: Term) : Term("", TF.getIntType(), listOf(arrayRef)) {
    fun getArrayRef() = subterms[0]

    override fun print() = "${getArrayRef()}.length"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val arrayRef = t.transform(getArrayRef())
        return when {
            arrayRef == getArrayRef() -> this
            else -> t.tf.getArrayLength(arrayRef)
        }
    }
}