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
class ForEachPredicate(
    val start: Term,
    val end: Term,
    val body: Term,
    @Required override val type: PredicateType = PredicateType.State(),
    @Required @Contextual override val location: Location = Location()
) : Predicate() {
    override val operands by lazy { listOf(this.start, this.end, this.body) }

    override fun print() = "forEach($start, $end, $body)"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tStart = t.transform(start)
        val tEnd = t.transform(end)
        val tBody = t.transform(body)
        return when {
            tStart == start && tEnd == end && tBody == body -> this
            else -> predicate(type, location) { forEach(tStart, tEnd, tBody) }
        }
    }
}