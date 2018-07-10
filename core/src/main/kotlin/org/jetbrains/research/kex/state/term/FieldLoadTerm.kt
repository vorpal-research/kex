package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type

class FieldLoadTerm(type: Type, owner: Term, name: Term) : Term("", type, listOf(owner, name)) {
    fun getOwner() = subterms[0]
    fun getFieldName() = subterms[1]
    fun getClass() = (getOwner().type as? ClassType)?.`class` ?: unreachable { log.error("Non-class owner of field") }

    fun isStatic() = getOwner() is ConstClassTerm

    override fun print() = "${getOwner()}.${getFieldName()}"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val owner = t.transform(getOwner())
        val fieldName = t.transform(getFieldName())
        return when {
            owner == getOwner() && fieldName == getFieldName() -> this
            else -> t.tf.getFieldLoad(type, owner, fieldName)
        }
    }
}