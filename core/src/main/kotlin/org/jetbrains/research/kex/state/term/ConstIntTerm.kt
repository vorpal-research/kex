package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.state.transformer.Transformer

class ConstIntTerm(val value: Int): Term(value.toString(), KexInt, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}