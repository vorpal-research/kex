package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexNull
import org.jetbrains.research.kex.state.transformer.Transformer

class NullTerm : Term("null", KexNull, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}