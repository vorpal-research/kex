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
class FieldInitializerPredicate(
        val field: Term,
        val value: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @Contextual override val location: Location = Location()) : Predicate() {

    override val operands by lazy { listOf(this.field, this.value) }

    override fun print() = "init *($field) = $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tfield = t.transform(field)
        val tvalue = t.transform(value)
        return when {
            tfield == field && tvalue == value -> this
            else -> predicate(type, location) { tfield.initialize(tvalue) }
        }
    }
}