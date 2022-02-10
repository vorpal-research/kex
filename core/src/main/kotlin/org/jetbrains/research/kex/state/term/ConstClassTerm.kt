package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ConstClassTerm(override val type: KexType, val constantType: KexType) : Term() {
    companion object {
        const val TYPE_INDEX_PROPERTY = "type_index"
    }

    override val name = "$constantType.class"
    override val subTerms by lazy { listOf<Term>() }

    override fun <T : Transformer<T>> accept(t: Transformer<T>) = this
}