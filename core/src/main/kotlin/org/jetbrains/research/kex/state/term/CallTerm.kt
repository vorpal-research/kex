package org.jetbrains.research.kex.state.term

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Method

@InheritorOf("Term")
@Serializable
class CallTerm(
        override val type: KexType,
        val owner: Term,
        @ContextualSerialization val method: Method,
        val arguments: List<Term>) : Term() {
    override val name = "$owner.${method.name}(${arguments.joinToString()})"
    override val subterms by lazy { listOf(owner) + arguments }

    val isStatic: Boolean
        get() = owner is ConstClassTerm

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val towner = t.transform(owner)
        val targuments = arguments.map { t.transform(it) }
        return when {
            towner == owner && targuments == arguments -> this
            else -> t.tf.getCall(method, towner, targuments)
        }
    }
}