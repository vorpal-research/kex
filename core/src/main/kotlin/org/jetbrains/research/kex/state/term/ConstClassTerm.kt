package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.Class

class ConstClassTerm(type: Type, val `class`: Class) : Term("$`class`.class", type, listOf()) {
    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}