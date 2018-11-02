package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
class BoundStorePredicate(ptr: Term, bound: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, arrayListOf(ptr, bound)) {

    val ptr: Term
        get() = operands[0]

    val bound: Term
        get() = operands[1]

    override fun print() = "bound($ptr, $bound)"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val nptr = t.transform(ptr)
        val nbound = t.transform(bound)
        return when {
            nptr == ptr && nbound == bound -> this
            else -> t.pf.getBoundStore(nptr, nbound, type, location)
        }
    }
}