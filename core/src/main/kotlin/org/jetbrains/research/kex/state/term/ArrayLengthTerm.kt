package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArrayLengthTerm(override val type: KexType, val arrayRef: Term) : Term() {
    override val name = "$arrayRef.length"
    override val subterms by lazy { listOf(arrayRef) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
            when (val tarrayRef = t.transform(arrayRef)) {
                arrayRef -> this
                else -> term { tf.getArrayLength(tarrayRef) }
            }
}