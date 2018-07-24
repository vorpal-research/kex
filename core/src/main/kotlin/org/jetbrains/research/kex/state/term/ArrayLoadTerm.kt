package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type

class ArrayLoadTerm(type: Type, arrayRef: Term) : Term("", type, listOf(arrayRef)) {
    val arrayRef get() = subterms[0]

    override fun print() = "*($arrayRef)"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tarrayRef = t.transform(arrayRef)
        return when {
            tarrayRef == arrayRef -> this
            else -> t.tf.getArrayLoad(type, tarrayRef)
        }
    }
}