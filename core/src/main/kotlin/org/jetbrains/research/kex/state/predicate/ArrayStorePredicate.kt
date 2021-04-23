package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class ArrayStorePredicate(
        val arrayRef: Term,
        val value: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @Contextual override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(arrayRef, value) }

    val componentType: KexType
        get() = (arrayRef.type as? KexArray)?.element ?: unreachable { log.error("Non-array type of array ref") }

    override fun print() = "*($arrayRef) = $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val ref = t.transform(arrayRef)
        val store = t.transform(value)
        return when {
            ref == arrayRef && store == value -> this
            else -> predicate(type, location) { ref.store(store) }
        }
    }
}