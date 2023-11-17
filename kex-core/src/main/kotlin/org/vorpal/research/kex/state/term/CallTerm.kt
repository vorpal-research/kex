package org.vorpal.research.kex.state.term

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.Method

@InheritorOf("Term")
@Serializable
class CallTerm(
        override val type: KexType,
        val owner: Term,
        @Contextual val method: Method,
        val arguments: List<Term>) : Term() {
    override val name = "$owner.${method.name}(${arguments.joinToString()})"
    override val subTerms by lazy { listOf(owner) + arguments }

    val isStatic: Boolean
        get() = owner is StaticClassRefTerm

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tOwner = t.transform(owner)
        val tArguments = arguments.map { t.transform(it) }
        return when {
            tOwner == owner && tArguments == arguments -> this
            else -> term { termFactory.getCall(method, tOwner, tArguments) }
        }
    }

    override fun hashCode() = 31 * super.hashCode() + method.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as CallTerm
        return super.equals(other) && this.method == other.method
    }
}
