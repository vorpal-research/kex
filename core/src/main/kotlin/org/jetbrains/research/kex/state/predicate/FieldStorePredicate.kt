package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.toInt
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type

class FieldStorePredicate : Predicate {
    val `class`: Class
    val fieldType: Type
    val isStatic: Boolean

    constructor(objectRef: Term, fieldName: Term, fieldType: Type, value: Term, type: PredicateType = PredicateType.State())
            : super(type, listOf(objectRef, fieldName, value)) {
        this.`class` = (objectRef.type as? ClassType)?.`class` ?: unreachable { log.error("Non-class type of object ref") }
        this.fieldType = fieldType
        isStatic = false
    }

    constructor(`class`: Class, fieldName: Term, fieldType: Type, value: Term, type: PredicateType = PredicateType.State())
            : super(type, listOf(fieldName, value)) {
        this.`class` = `class`
        this.fieldType = fieldType
        isStatic = true
    }

    fun getObjectRef() = when {
        !isStatic -> operands[0]
        else -> unreachable { log.error("Trying to get object reference of static store") }
    }

    fun getFieldName() = operands[1 - isStatic.toInt()]
    fun getValue() = operands[2 - isStatic.toInt()]

    override fun print() = when {
        isStatic -> "${`class`}.${getFieldName()} = ${getValue()}"
        else -> "${getObjectRef()}.${getFieldName()} = ${getValue()}"
    }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val objectRef = if (isStatic) null else t.transform(getObjectRef())
        val name = t.transform(getFieldName())
        val value = t.transform(getValue())
        return when {
            objectRef == null && name == getFieldName() && value == getValue() -> this
            objectRef == null && !(name == getFieldName() && value == getValue()) -> t.pf.getFieldStore(`class`, name, fieldType, value)
            objectRef == getObjectRef() && name == getFieldName() && value == getValue() -> this
            else -> t.pf.getFieldStore(objectRef!!, name, fieldType, value)
        }
    }

}