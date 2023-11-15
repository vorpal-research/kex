package org.vorpal.research.kex.state.predicate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class NewArrayInitializerPredicate(
    val lhv: Term,
    val length: Term,
    val elements: List<Term>,
    val elementType: KexType,
    @Required
    override val type: PredicateType = PredicateType.State(),
    @Required
    @Contextual
    override val location: Location = Location()
) : Predicate() {
    override val operands by lazy { listOf(lhv, length) + elements }

    override fun print() = "$lhv = initialize $elementType[$length] ${
        elements.joinToString(
            separator = ", ",
            prefix = "{",
            postfix = "}"
        )
    }"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tLhv = t.transform(lhv)
        val tLength = t.transform(length)
        val tElements = elements.map { t.transform(it) }
        return when {
            tLhv == lhv && tLength == length && tElements == elements -> this
            else -> predicate(type, location) { tLhv.initializeNew(tLength, tElements) }
        }
    }
}
