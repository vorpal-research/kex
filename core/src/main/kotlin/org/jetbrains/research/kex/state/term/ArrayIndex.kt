package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ArrayIndexTerm(type: KexType, arrayRef: Term, index: Term)
    : Term("$arrayRef[$index]", type, listOf(arrayRef, index)) {

    val arrayRef: Term
        get() = subterms[0]

    val index: Term
        get() = subterms[1]

    override fun print() = name

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tarrayRef = t.transform(arrayRef)
        val tindex = t.transform(index)
        return when {
            tarrayRef == arrayRef && tindex == index -> this
            else -> t.tf.getArrayIndex(type, tarrayRef, tindex)
        }
    }

}