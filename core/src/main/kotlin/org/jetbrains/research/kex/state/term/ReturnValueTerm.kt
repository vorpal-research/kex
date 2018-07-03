package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

class ReturnValueTerm(type: Type, val method: Method) : Term("<retval>", type, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}