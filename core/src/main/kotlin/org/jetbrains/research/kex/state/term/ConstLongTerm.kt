package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexLong
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ConstLongTerm(val value: Long) : Term(value.toString(), KexLong(), listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}