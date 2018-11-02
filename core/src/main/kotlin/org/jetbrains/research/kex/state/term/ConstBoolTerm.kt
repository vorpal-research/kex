package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ConstBoolTerm(val value: Boolean) : Term(value.toString(), KexBool, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}