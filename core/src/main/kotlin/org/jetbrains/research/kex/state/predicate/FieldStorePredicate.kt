package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type

class FieldStorePredicate(field: Term,
                          val fieldType: Type,
                          value: Term,
                          type: PredicateType = PredicateType.State(),
                          location: Location = Location()) : Predicate(type, location, listOf(field, value)) {
    val field get() = operands[0]
    val value get() = operands[1]
    override fun print() = "*($field) = $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tfield = t.transform(field)
        val tvalue = t.transform(value)
        return when {
            tfield == field && tvalue == value -> this
            else -> t.pf.getFieldStore(tfield, fieldType, tvalue, type)
        }
    }

}