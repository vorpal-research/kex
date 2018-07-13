package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type

class FieldTerm(type: Type, owner: Term, name: Term) : Term("${owner.print()}.${name.name}", type, listOf(owner, name)) {
    fun getOwner() = subterms[0]
    fun getFieldName() = subterms[1]

    fun getClass() = (getOwner().type as? ClassType)?.`class` ?: unreachable { log.error("Non-class owner in field term") }
    fun isStatic() = getOwner() is ConstClassTerm

    override fun print() = name

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val owner = t.transform(getOwner())
        val name = t.transform(getFieldName())
        return when {
            owner == getOwner() && name == getFieldName() -> this
            else -> t.tf.getField(type, owner, name)
        }
    }

}