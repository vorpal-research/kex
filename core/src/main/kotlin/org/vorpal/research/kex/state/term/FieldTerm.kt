package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

@InheritorOf("Term")
@Serializable
class FieldTerm(override val type: KexType, val owner: Term, val fieldName: String) : Term() {
    init {
        ktassert(owner.type is KexClass)
    }

    override val name = "$owner.$fieldName"
    override val subTerms by lazy { listOf(owner) }

    val isStatic: Boolean
        get() = owner is StaticClassRefTerm

    val klass: String
        get() = (owner.type as? KexClass)?.klass
            ?: unreachable { log.error("Non-class owner in field term") }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
        when (val tOwner = t.transform(owner)) {
            owner -> this
            else -> term { termFactory.getField(type, tOwner, fieldName) }
        }

}
