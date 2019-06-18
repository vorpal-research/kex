package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable

@InheritorOf("Term")
@Serializable
class FieldLoadTerm(override val type: KexType, val field: Term) : Term() {
    override val name = "*($field)"
    override val subterms: List<Term>
        get() = listOf(this.field)

    val isStatic
        get() = (field as? FieldTerm)?.isStatic ?: unreachable { log.error("Non-field term in field load") }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tfield = t.transform(field)
        return when (tfield) {
            field -> this
            else -> t.tf.getFieldLoad(type, tfield)
        }
    }
}