package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class NegTerm(type: KexType, operand: Term) : Term("", type, listOf(operand)) {

    val operand: Term
        get() = subterms[0]

    override fun print() = "-$operand"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val toperand = t.transform(operand)
        return if (toperand == operand) this else t.tf.getNegTerm(toperand)
    }
}