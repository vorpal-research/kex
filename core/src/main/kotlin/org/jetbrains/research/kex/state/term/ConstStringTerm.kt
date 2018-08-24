package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

class ConstStringTerm(type: KexType, value: String) : Term(value, type, listOf()) {
    override fun print() = "\'$name\'"
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}