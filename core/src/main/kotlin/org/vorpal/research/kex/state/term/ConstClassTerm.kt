package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ConstClassTerm(override val type: KexType, val constantType: KexType) : Term() {
    companion object {
        const val TYPE_INDEX_PROPERTY = "type_index"
        const val NAME_PROPERTY = "name"
        const val MODIFIERS_PROPERTY = "modifiers"
    }

    override val name = "$constantType.class"
    override val subTerms by lazy { listOf<Term>() }

    override fun <T : Transformer<T>> accept(t: Transformer<T>) = this
}