package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexByte
import org.jetbrains.research.kex.state.transformer.Transformer

class ConstByteTerm(val value: Byte) : Term(value.toString(), KexByte, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}