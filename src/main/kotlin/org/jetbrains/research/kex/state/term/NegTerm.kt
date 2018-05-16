package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer

class NegTerm(operand: Term) : Term("", operand.type, listOf(operand)) {
    fun getOperand() = subterms[0]

    override fun print() = "-${getOperand()}"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val operand = t.transform(getOperand())
        return if (operand == getOperand()) this else t.tf.getNegTerm(operand)
    }
}