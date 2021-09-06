package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class IteTerm(
    override val type: KexType,
    val cond: Term,
    val trueValue: Term,
    val falseValue: Term
) : Term() {
    override val name = "($cond) ? ($trueValue) : ($falseValue)"
    override val subTerms by lazy { listOf(cond, trueValue, falseValue) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tCond = t.transform(cond)
        val tTrue = t.transform(trueValue)
        val tFalse = t.transform(falseValue)
        return when {
            tCond == cond && tTrue == trueValue && tFalse == falseValue -> this
            else -> term { ite(type, tCond, tTrue, tFalse) }
        }
    }
}