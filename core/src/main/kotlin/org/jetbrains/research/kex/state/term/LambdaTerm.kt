package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class LambdaTerm (
    override val type: KexType,
    val parameters: List<Term>,
    val body: PredicateState
) : Term() {
    override val name: String
        get() = "\\(${parameters.joinToString(", ")}) -> { ... }"
    override val subTerms: List<Term>
        get() = parameters

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tParams = parameters.map { t.transform(it) }
        val tBody = t.transform(body)
        return when {
            parameters == tParams && body == tBody -> this
            else -> term { lambda(type, tParams, tBody) }
        }
    }
}