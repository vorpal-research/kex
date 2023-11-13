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
class DefaultSwitchPredicate(
        val cond: Term,
        val cases: List<Term>,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @Contextual override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(cond) + cases }

    override fun print() = "$cond !in (${cases.joinToString(", ")})"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tCond = t.transform(cond)
        val tCases = cases.map { t.transform(it) }
        return when {
            tCond == cond && tCases == cases -> this
            else -> predicate(type, location) { tCond `!in` tCases }
        }
    }
}