package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ValueTerm(override val type: KexType, val valueName: Term) : Term() {
    override val name = (valueName as ConstStringTerm).value
    override val subTerms by lazy { listOf(valueName) }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}