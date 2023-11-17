package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayContainsTerm(
    val array: Term,
    val value: Term
) : Term() {
    override val type = KexBool
    override val name = "$value in $array"
    override val subTerms by lazy { listOf(array, value) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tArray = t.transform(array)
        val tValue = t.transform(value)
        return when {
            tArray == array && tValue == value -> this
            else -> term { tValue `in` tArray }
        }
    }
}
