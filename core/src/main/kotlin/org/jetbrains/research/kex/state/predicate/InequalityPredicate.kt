package org.jetbrains.research.kex.state.predicate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class InequalityPredicate(
        val lhv: Term,
        val rhv: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @Contextual override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(lhv, rhv) }

    override fun print() = "$lhv != $rhv"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val nLhv = t.transform(lhv)
        val nRhv = t.transform(rhv)
        return when {
            nLhv == lhv && nRhv == rhv -> this
            else -> predicate(type, location) { nLhv inequality nRhv }
        }
    }
}