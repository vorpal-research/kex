package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type

class FieldStorePredicate(field: Term, val fieldType: Type, value: Term, type: PredicateType = PredicateType.State())
    : Predicate(type, listOf(field, value)) {

    fun getField() = operands[0]
    fun getValue() = operands[1]
    override fun print() = "*(${getField()}) = ${getValue()}"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val field = t.transform(getField())
        val value = t.transform(getValue())
        return when {
            field == getField() && value == getValue() -> this
            else -> t.pf.getFieldStore(field, fieldType, value, type)
        }
    }

}