package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexDouble
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ConstDoubleTerm(val value: Double) : Term() {
    override val name = value.toString()
    override val type: KexType = KexDouble()
    override val subterms by lazy { listOf<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}