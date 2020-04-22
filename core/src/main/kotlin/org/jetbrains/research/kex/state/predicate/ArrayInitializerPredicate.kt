package org.jetbrains.research.kex.state.predicate

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class ArrayInitializerPredicate(
        val arrayRef: Term,
        val value: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @ContextualSerialization override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(arrayRef, value) }

    override fun print() = "init *($arrayRef) = $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val ref = t.transform(arrayRef)
        val store = t.transform(value)
        return when {
            ref == arrayRef && store == value -> this
            else -> predicate(type, location) { ref.initialize(store) }
        }
    }
}