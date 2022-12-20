package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class EqualsTerm(
    val lhv: Term,
    val rhv: Term
) : Term() {
    override val name = "${lhv}.equals($rhv)"
    override val type: KexType = KexBool
    override val subTerms: List<Term> by lazy {
        listOf(lhv, rhv)
    }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
        val tLhv = t.transform(lhv)
        val tRhv = t.transform(rhv)
        return when {
            tLhv == lhv && tRhv == rhv -> this
            else -> term { tLhv equls tRhv }
        }
    }
}
