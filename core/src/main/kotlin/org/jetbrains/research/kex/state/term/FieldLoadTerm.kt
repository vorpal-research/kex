package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

@InheritorOf("Term")
@Serializable
class FieldLoadTerm(override val type: KexType, val field: Term) : Term() {
    override val name = "*($field)"
    override val subTerms by lazy { listOf(this.field) }

    val isStatic
        get() = (field as? FieldTerm)?.isStatic ?: unreachable { log.error("Non-field term in field load") }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
            when (val tField = t.transform(field)) {
                field -> this
                else -> term { tf.getFieldLoad(type, tField) }
             }
}