package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.type.Type

class NewArrayPredicate(lhv: Term,
                        dimentions: List<Term>,
                        val elementType: Type,
                        type: PredicateType = PredicateType.State(),
                        location: Location = Location()) :
        Predicate(type, location, listOf(lhv).plus(dimentions)) {
    val lhv get() = operands[0]
    val dimentions get() = operands.drop(1)
    val numDimentions get() = operands.size - 1

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
            tlhv == lhv && tdimentions.contentEquals(dimentions) -> this
            else -> t.pf.getNewArray(tlhv, tdimentions, type)
        }
    }
}