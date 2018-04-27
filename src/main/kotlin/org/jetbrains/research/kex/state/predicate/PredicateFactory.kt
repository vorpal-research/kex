package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.type.ArrayType

object PredicateFactory : Loggable {
    fun getArrayStore(arrayRef: Term, storeTerm: Term, type: PredicateType = PredicateType.State()) = StorePredicate(arrayRef, storeTerm, type)

    fun getArrayLoad(lhv: Term, loadTerm: Term) = getEqualityPredicate(lhv, loadTerm)

    fun getNew(lhv: Term) = NewPredicate(lhv)
    fun getNewArray(lhv: Term, numElements: Term, type: PredicateType = PredicateType.State()): Predicate {
        val arrayType = lhv.type as? ArrayType
                ?: error(log.error("Trying to create new array predicate with non-array type"))
        return NewArrayPredicate(lhv, numElements, arrayType.component, type)
    }

    fun getMultipleNewArray(lhv: Term, dimensions: Array<Term>, type: PredicateType = PredicateType.State()): Predicate {
        var current = lhv.type
        dimensions.forEach {
            current = (current as? ArrayType
                    ?: error(log.error("Trying to create new array predicate with non-array type"))).component
        }
        return MultiNewArrayPredicate(lhv, dimensions, current, type)
    }

    fun getEqualityPredicate(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State()) = EqualityPredicate(lhv, rhv, type)
}