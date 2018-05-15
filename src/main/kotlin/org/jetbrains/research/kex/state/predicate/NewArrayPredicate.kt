package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type

class NewArrayPredicate(lhv: Term, numElements: Term, val elementType: Type, type: PredicateType = PredicateType.State())
    : Predicate(type, arrayOf(lhv, numElements)) {

    fun getLhv() = operands[0]
    fun getNumElements() = operands[1]

    override fun print() = "${getLhv()} = new $elementType[${getNumElements()}]"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val lhv = t.transform(getLhv())
        val numElements = t.transform(getNumElements())
        return when {
            lhv == getLhv() && numElements == getNumElements() -> this
            else -> t.pf.getNewArray(lhv, numElements, type)
        }
    }
}