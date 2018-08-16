package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF

class ConstShortTerm(val value: Short) : Term(value.toString(), TF.shortType, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}