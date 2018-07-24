package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class NegTerm(type: Type, operand: Term) : Term("", type, listOf(operand)) {
    val operand get() = subterms[0]

    override fun print() = "-$operand"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val toperand = t.transform(operand)
        return if (toperand == operand) this else t.tf.getNegTerm(toperand)
    }
}