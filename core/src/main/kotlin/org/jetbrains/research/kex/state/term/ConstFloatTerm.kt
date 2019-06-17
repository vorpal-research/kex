package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexFloat
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ConstFloatTerm(val value: Float) : Term(value.toString(), KexFloat(), listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}