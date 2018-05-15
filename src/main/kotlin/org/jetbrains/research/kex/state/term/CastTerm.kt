package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class CastTerm(type: Type, operand: Term) : Term("", type, arrayOf(operand)) {
    fun getOperand() = subterms[0]
    override fun print() = "($type) ${getOperand()}"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val operand = t.transform(getOperand())
        return when {
            operand == getOperand() -> this
            else -> t.tf.getCast(type, operand)
        }
    }
}