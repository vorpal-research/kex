package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable

class FieldTerm(type: KexType, owner: Term, name: Term) : Term("${owner.print()}.${name.name}", type, listOf(owner, name)) {

    val owner: Term
        get() = subterms[0]

    val fieldName: Term
        get() = subterms[1]

    val isStatic: Boolean
        get() = owner is ConstClassTerm

    fun getClass() = (owner.type as? KexClass)?.`class` ?: unreachable { log.error("Non-class owner in field term") }

    override fun print() = name
    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val towner = t.transform(owner)
        val tname = t.transform(fieldName)
        return when {
            towner == owner && tname == fieldName -> this
            else -> t.tf.getField(type, towner, tname)
        }
    }

}