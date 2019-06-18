package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class NegTerm(override val type: KexType, val operand: Term) : Term() {
    override val name = "-$operand"
    override val subterms: List<Term>
        get() = listOf(operand)

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val toperand = t.transform(operand)
        return if (toperand == operand) this else t.tf.getNegTerm(toperand)
    }
}