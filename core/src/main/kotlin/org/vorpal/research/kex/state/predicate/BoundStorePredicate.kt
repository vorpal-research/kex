package org.vorpal.research.kex.state.predicate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class BoundStorePredicate(
        val ptr: Term,
        val bound: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @Contextual override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(ptr, bound) }

    override fun print() = "bound($ptr, $bound)"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val nPtr = t.transform(ptr)
        val nBound = t.transform(bound)
        return when {
            nPtr == ptr && nBound == bound -> this
            else -> predicate(type, location) { nPtr.bound(nBound) }
        }
    }
}