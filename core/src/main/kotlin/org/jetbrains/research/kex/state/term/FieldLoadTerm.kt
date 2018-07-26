package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.Type

class FieldLoadTerm(type: Type, fieldRef: Term) : Term("", type, listOf(fieldRef)) {

    val field: Term
        get() = subterms[0]

    val isStatic
        get() = (field as? FieldTerm)?.isStatic ?: unreachable { log.error("Non-field term in field load") }

    override fun print() = "*($field)"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tfield = t.transform(field)
        return when {
            tfield == field -> this
            else -> t.tf.getFieldLoad(type, tfield)
        }
    }
}