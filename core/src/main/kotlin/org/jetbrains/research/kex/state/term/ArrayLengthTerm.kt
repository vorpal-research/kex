package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayLengthTerm(val arrayRef: Term) : Term() {
    override val type = KexInt()
    override val name = "$arrayRef.length"
    override val subTerms by lazy { listOf(arrayRef) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
            when (val tArrayRef = t.transform(arrayRef)) {
                arrayRef -> this
                else -> term { tf.getArrayLength(tArrayRef) }
            }
}