package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class ArrayIndexTerm(type: Type, arrayRef: Term, index: Term)
    : Term("$arrayRef[$index]", type, listOf(arrayRef, index)) {
    fun getArrayRef() = subterms[0]
    fun getIndex() = subterms[1]

    override fun print() = name

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val arrayRef = t.transform(getArrayRef())
        val index = t.transform(getIndex())
        return when {
            arrayRef == getArrayRef() && index == getIndex() -> this
            else -> t.tf.getArrayIndex(type, arrayRef, index)
        }
    }

}