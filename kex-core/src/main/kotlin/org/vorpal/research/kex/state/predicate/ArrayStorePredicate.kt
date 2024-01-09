package org.vorpal.research.kex.state.predicate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

@InheritorOf("Predicate")
@Serializable
class ArrayStorePredicate(
        val arrayRef: Term,
        val value: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @Contextual override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(arrayRef, value) }

    @Suppress("unused")
    val componentType: KexType
        get() = (arrayRef.type as? KexReference)?.reference ?: unreachable { log.error("Non-array type of array ref") }

    override fun print() = "*($arrayRef) := $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val ref = t.transform(arrayRef)
        val store = t.transform(value)
        return when {
            ref == arrayRef && store == value -> this
            else -> predicate(type, location) { ref.store(store) }
        }
    }
}
