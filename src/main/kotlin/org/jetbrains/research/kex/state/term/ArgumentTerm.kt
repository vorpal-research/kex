package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class ArgumentTerm(val index: Int, type: Type) : Term("arg$$index", type, arrayOf()) {
    override fun print() = name

    override fun <T> accept(t: Transformer<T>) = this
}