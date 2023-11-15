package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

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

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + parameters.hashCode()
        result = 31 * result + body.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return other is LambdaTerm && type == other.type && parameters == other.parameters && body == other.body
    }
}
