package org.jetbrains.research.kex.state.term

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Method

@InheritorOf("Term")
@Serializable
class ReturnValueTerm(
        override val type: KexType,
        @ContextualSerialization val method: Method) : Term() {
    override val name = "<retval>"
    override val subterms: List<Term>
        get() = listOf()

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}