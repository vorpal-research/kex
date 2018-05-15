package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF

class ConstCharTerm(val value: Char) : Term(value.toString(), TF.getCharType(), arrayOf()) {
    override fun print() = name
    override fun <T> accept(t: Transformer<T>) = this
}