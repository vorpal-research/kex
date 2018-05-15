package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer

class StorePredicate(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State()) : Predicate(type, arrayOf(lhv, rhv)) {
    fun getLhv() = operands[0]
    fun getStoreVal() = operands[1]

    override fun print() = "${getLhv()} = ${getStoreVal()}"

    override fun <T> accept(t: Transformer<T>): Predicate {
        val lhv = t.transform(getLhv())
        val store = t.transform(getStoreVal())
        return when {
            lhv == getLhv() && store == getStoreVal() -> this
            else -> t.pf.getStore(lhv, store, type)
        }
    }
}