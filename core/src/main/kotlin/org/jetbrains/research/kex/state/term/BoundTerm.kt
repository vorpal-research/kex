package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class BoundTerm(override val type: KexType, val ptr: Term) : Term() {
    override val name = "bound($ptr)"
    override val subterms by lazy { listOf(ptr) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val nptr = t.transform(ptr)
        return when (ptr) {
            nptr -> this
            else -> t.tf.getBound(nptr)
        }
    }
}