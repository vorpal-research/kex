package org.jetbrains.research.kex.state.predicate

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class BoundStorePredicate(
        val ptr: Term,
        val bound: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @ContextualSerialization override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(ptr, bound) }

    override fun print() = "bound($ptr, $bound)"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val nptr = t.transform(ptr)
        val nbound = t.transform(bound)
        return when {
            nptr == ptr && nbound == bound -> this
            else -> predicate(type, location) { nptr.bound(nbound) }
        }
    }
}