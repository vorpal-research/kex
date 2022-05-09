package org.vorpal.research.kex.state.predicate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class NewArrayPredicate(
    val lhv: Term,
    val dimensions: List<Term>,
    val elementType: KexType,
    @Required override val type: PredicateType = PredicateType.State(),
    @Required @Contextual override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(lhv) + dimensions }

    val numDimensions: Int
        get() = dimensions.size

    override fun print() = "$lhv = new $elementType${dimensions.joinToString { "[$it]" }}"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tLhv = t.transform(lhv)
        val tDimensions = dimensions.map { t.transform(it) }
        return when {
            tLhv == lhv && tDimensions == dimensions -> this
            else -> predicate(type, location) { tLhv.new(tDimensions) }
        }
    }
}