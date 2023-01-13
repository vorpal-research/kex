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
class FieldInitializerPredicate(
    val field: Term,
    val value: Term,
    @Required override val type: PredicateType = PredicateType.State(),
    @Required @Contextual override val location: Location = Location()
) : Predicate() {
    override val operands by lazy { listOf(this.field, this.value) }

    override fun print() = "init *($field) = $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tField = t.transform(field)
        val tValue = t.transform(value)
        return when {
            tField == field && tValue == value -> this
            else -> predicate(type, location) { tField.initialize(tValue) }
        }
    }
}
