package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class LambdaTerm (
    override val type: KexType,
    val parameters: List<Term>,
    val body: Term
) : Term() {
    override val name: String
        get() = "\\(${parameters.joinToString(", ")}) -> { $body }"
    override val subTerms: List<Term>
        get() = parameters

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tParams = parameters.map { t.transform(it) }
        return when (parameters) {
            tParams -> this
            else -> term { lambda(type, tParams, body) }
        }
    }
}