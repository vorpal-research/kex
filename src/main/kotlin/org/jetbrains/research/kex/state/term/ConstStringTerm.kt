package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF

class ConstStringTerm(value: String) : Term(value, TF.getString(), arrayOf()) {
    override fun print() = "\"$name\""
    override fun <T> accept(t: Transformer<T>) = this
}