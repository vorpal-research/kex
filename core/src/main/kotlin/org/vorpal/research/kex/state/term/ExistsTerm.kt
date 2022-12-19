package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ExistsTerm(
    val start: Term,
    val end: Term,
    val body: Term
) : Term() {
    override val type = KexBool
    override val name = "exists($start, $end, $body)"
    override val subTerms by lazy { listOf(start, end, body) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tStart = t.transform(start)
        val tEnd = t.transform(end)
        val tBody = t.transform(body)
        return when {
            start == tStart && end == tEnd && body == tBody -> this
            else -> term { exists(tStart, tEnd, tBody) }
        }
    }
}
