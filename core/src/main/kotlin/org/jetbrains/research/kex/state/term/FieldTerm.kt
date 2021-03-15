package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class FieldTerm(override val type: KexType, val owner: Term, val fieldName: Term) : Term() {
    init {
        ktassert(owner.type is KexClass)
    }

    val fieldNameString = (fieldName as ConstStringTerm).value
    override val name = "$owner.$fieldNameString"
    override val subterms by lazy { listOf(owner, fieldName) }

    val isStatic: Boolean
        get() = owner is ConstClassTerm

    val klass: String
        get() = (owner.type as? KexClass)?.klass
                ?: unreachable { log.error("Non-class owner in field term") }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val towner = t.transform(owner)
        val tname = t.transform(fieldName)
        return when {
            towner == owner && tname == fieldName -> this
            else -> term { tf.getField(type, towner, tname) }
        }
    }

}
