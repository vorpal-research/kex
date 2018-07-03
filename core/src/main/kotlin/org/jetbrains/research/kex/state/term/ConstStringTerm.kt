package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class ConstStringTerm(type: Type, value: String) : Term(value, type, listOf()) {
    override fun print() = "\'$name\'"
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}