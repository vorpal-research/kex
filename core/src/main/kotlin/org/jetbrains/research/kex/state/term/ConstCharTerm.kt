package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexChar
import org.jetbrains.research.kex.state.transformer.Transformer

class ConstCharTerm(val value: Char) : Term(value.toString(), KexChar, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}