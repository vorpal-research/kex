package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type

class FieldStorePredicate(owner: Term, fieldName: Term, val fieldType: Type, value: Term, type: PredicateType = PredicateType.State())
    : Predicate(type, listOf(owner, fieldName, value)) {

    fun getOwner() = operands[0]
    fun getFieldName() = operands[1]
    fun getValue() = operands[2]
    fun getClass() = (getOwner().type as? ClassType)?.`class` ?: unreachable { log.error("Non-class owner of field") }

    override fun print() = "${getOwner()}.${getFieldName()} = ${getValue()}"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val owner = t.transform(getOwner())
        val name = t.transform(getFieldName())
        val value = t.transform(getValue())
        return when {
            owner == getOwner() && name == getFieldName() && value == getValue() -> this
            else -> t.pf.getFieldStore(owner, name, fieldType, value)
        }
    }

}