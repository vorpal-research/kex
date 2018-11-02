package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
class InequalityPredicate(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, arrayListOf(lhv, rhv)) {

    val lhv: Term
        get() = operands[0]

    val rhv: Term
        get() = operands[1]

    override fun print() = "$lhv != $rhv"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val nlhv = t.transform(lhv)
        val nrhv = t.transform(rhv)
        return when {
            nlhv == lhv && nrhv == rhv -> this
            else -> t.pf.getInequality(nlhv, nrhv, type, location)
        }
    }
}