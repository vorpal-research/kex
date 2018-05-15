package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class MultiNewArrayPredicate(lhv: Term, dimentions: Array<Term>, val elementType: Type, type: PredicateType = PredicateType.State())
    : Predicate(type, arrayOf(lhv).plus(dimentions)) {
    fun getLhv() = operands[0]
    fun getDimentions() = operands.drop(1).toTypedArray()
    fun getNumDimentions() = operands.size - 1

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("${getLhv()} = new $elementType")
        getDimentions().forEach { sb.append("[$it]") }
        return sb.toString()
    }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val lhv = t.transform(getLhv())
        val dimentions = getDimentions().map { t.transform(it) }.toTypedArray()
        return when {
            lhv == getLhv() && dimentions.contentEquals(getDimentions()) -> this
            else -> t.pf.getMultipleNewArray(lhv, dimentions, type)
        }
    }
}