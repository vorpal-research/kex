package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

class BoundTerm(type: KexType, ptr: Term) : Term("bound($ptr)", type, arrayListOf(ptr)) {
    val ptr: Term
        get() = subterms[0]

    override fun print() = name

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val nptr = t.transform(ptr)
        return when {
            ptr == nptr -> this
            else -> t.tf.getBound(nptr)
        }
    }
}