package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

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
                else -> term { termFactory.getFieldLoad((tField.type as KexReference).reference, tField) }
             }
}
