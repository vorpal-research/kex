package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexShort
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ConstShortTerm(val value: Short) : Term(value.toString(), KexShort(), listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}