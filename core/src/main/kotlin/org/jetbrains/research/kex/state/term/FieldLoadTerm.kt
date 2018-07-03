package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.Type

class FieldLoadTerm(type: Type, val classType: Type, operands: List<Term>) : Term("", type, operands) {
    val isStatic = operands.size == 1

    fun getObjectRef() = when {
        isStatic -> unreachable { log.error("Trying to get object reference of static load") }
        else -> subterms[0]
    }
    fun getFieldName() = if (isStatic) subterms[0] else subterms[1]

    override fun print(): String {
        val sb = StringBuilder()
        if (isStatic) sb.append(classType)
        else sb.append(getObjectRef())
        sb.append(".${getFieldName()}")
        return sb.toString()
    }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val objectRef = if (isStatic) null else t.transform(getObjectRef()!!)
        val fieldName = t.transform(getFieldName())
        return when {
            objectRef == null -> t.tf.getFieldLoad(type, classType, fieldName)
            objectRef == getObjectRef() && fieldName == getFieldName() -> this
            else -> t.tf.getFieldLoad(type, objectRef, fieldName)
        }
    }
}