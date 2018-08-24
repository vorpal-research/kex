package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexShort
import org.jetbrains.research.kex.state.transformer.Transformer

class ConstShortTerm(val value: Short) : Term(value.toString(), KexShort, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}