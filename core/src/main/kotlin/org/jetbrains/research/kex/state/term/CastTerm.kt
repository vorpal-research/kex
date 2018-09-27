package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

class CastTerm(type: KexType, operand: Term) : Term("", type, listOf(operand)) {

    val operand: Term
        get() = subterms[0]

    override fun print() = "($operand as $type)"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val toperand = t.transform(operand)
        return when (toperand) {
            operand -> this
            else -> t.tf.getCast(type, toperand)
        }
    }
}