package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
class FieldStorePredicate(field: Term,
                          value: Term,
                          type: PredicateType = PredicateType.State(),
                          location: Location = Location()) : Predicate(type, location, listOf(field, value)) {
    val field: Term
        get() = operands[0]

    val value: Term
        get() = operands[1]

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