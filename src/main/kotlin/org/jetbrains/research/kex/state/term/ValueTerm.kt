package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class ValueTerm(type: Type, valueName: String) : Term(valueName, type, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}