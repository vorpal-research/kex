package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable

@InheritorOf("Term")
@Serializable
class FieldTerm(override val type: KexType, val owner: Term, val fieldName: Term) : Term() {
    override val name = "$owner.${(fieldName as ConstStringTerm).value}"
    override val subterms by lazy { listOf(owner, fieldName) }

    val isStatic: Boolean
        get() = owner is ConstClassTerm

    fun getClass() = (owner.type as? KexClass)?.`class` ?: unreachable { log.error("Non-class owner in field term") }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val towner = t.transform(owner)
        val tname = t.transform(fieldName)
        return when {
            towner == owner && tname == fieldName -> this
            else -> t.tf.getField(type, towner, tname)
        }
    }

}