package org.jetbrains.research.kex.state.predicate

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
@Serializable
class NewArrayPredicate(
        val lhv: Term,
        val dimentions: List<Term>,
        val elementType: KexType,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @ContextualSerialization override val location: Location = Location()) : Predicate() {
    override val operands by lazy { listOf(lhv) + dimentions }

    val numDimentions: Int
        get() = dimentions.size

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$lhv = new $elementType")
        dimentions.forEach { sb.append("[$it]") }
        return sb.toString()
    }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tlhv = t.transform(lhv)
        val tdimentions = dimentions.map { t.transform(it) }
        return when {
            tlhv == lhv && tdimentions == dimentions -> this
            else -> t.pf.getNewArray(tlhv, tdimentions, type)
        }
    }
}