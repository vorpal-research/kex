package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF

class ConstBoolTerm(val value: Boolean) : Term(value.toString(), TF.getBoolType(), arrayOf()) {
    override fun print() = name
    override fun <T> accept(t: Transformer<T>) = this
}