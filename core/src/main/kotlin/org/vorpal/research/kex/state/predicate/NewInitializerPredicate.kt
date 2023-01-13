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
class NewInitializerPredicate(
    val lhv: Term,
    @Required override val type: PredicateType = PredicateType.State(),
    @Required @Contextual override val location: Location = Location()
) : Predicate() {
    override val operands by lazy { listOf(lhv) }

    override fun print() = "$lhv = initialize ${lhv.type}"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate = when (val tLhv = t.transform(lhv)) {
        lhv -> this
        else -> predicate(type, location) { tLhv.initializeNew() }
    }
}
