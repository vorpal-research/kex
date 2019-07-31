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
class DefaultSwitchPredicate(
        val cond: Term,
        val cases: List<Term>,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @ContextualSerialization override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(cond) + cases }

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$cond !in (")
        val cases = cases
        cases.take(1).forEach { sb.append(it) }
        cases.drop(1).forEach { sb.append(", $it") }
        sb.append(")")
        return sb.toString()
    }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tcond = t.transform(cond)
        val tcases = cases.map { t.transform(it) }
        return when {
            tcond == cond && tcases == cases -> this
            else -> predicate(type, location) { tcond `!in` tcases }
        }
    }
}