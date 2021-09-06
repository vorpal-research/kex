package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class CastTerm(override val type: KexType, val operand: Term) : Term() {
    override val name = "($operand as $type)"
    override val subTerms by lazy { listOf(operand) }


    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
            when (val tOperand = t.transform(operand)) {
                operand -> this
                else -> term { tf.getCast(type, tOperand) }
             }
}