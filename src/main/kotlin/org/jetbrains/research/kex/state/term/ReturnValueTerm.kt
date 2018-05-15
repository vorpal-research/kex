package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Method

class ReturnValueTerm(val method: Method) : Term("<retval>", method.desc.retval, arrayOf()) {
    override fun print() = name
    override fun <T> accept(t: Transformer<T>) = this
}