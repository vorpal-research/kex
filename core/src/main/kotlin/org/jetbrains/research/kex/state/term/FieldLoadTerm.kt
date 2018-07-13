package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type

class FieldLoadTerm(type: Type, fieldRef: Term) : Term("", type, listOf(fieldRef)) {
    fun getField() = subterms[0]
    fun isStatic() = (getField() as? FieldTerm)?.isStatic() ?: unreachable { log.error("Non-field term in field load") }

    override fun print() = "*(${getField()})"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val field = t.transform(getField())
        return when {
            field == getField() -> this
            else -> t.tf.getFieldLoad(type, field)
        }
    }
}