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
class FieldStorePredicate(
        val field: Term,
        val value: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @ContextualSerialization override val location: Location = Location()) : Predicate() {
    override val operands: List<Term>
        get() = listOf(this.field, this.value)

    override fun print() = "*($field) = $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tfield = t.transform(field)
        val tvalue = t.transform(value)
        return when {
            tfield == field && tvalue == value -> this
            else -> t.pf.getFieldStore(tfield, tvalue, type)
        }
    }

}