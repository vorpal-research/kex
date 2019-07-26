package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class CastTerm(override val type: KexType, val operand: Term) : Term() {
    override val name = "($operand as $type)"
    override val subterms by lazy { listOf(operand) }


    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val toperand = t.transform(operand)
        return when (toperand) {
            operand -> this
            else -> t.tf.getCast(type, toperand)
        }
    }
}