package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF

class NullTerm : Term("null", TF.getNullType(), arrayOf()) {
    override fun print() = name
    override fun <T> accept(t: Transformer<T>) = this
}