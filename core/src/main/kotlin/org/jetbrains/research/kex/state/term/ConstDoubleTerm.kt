package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexDouble
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ConstDoubleTerm(val value: Double) : Term(value.toString(), KexDouble(), listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}