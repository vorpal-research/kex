package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.type.ArrayType

object PredicateFactory : Loggable {
    fun getCall(callTerm: Term, type: PredicateType = PredicateType.State()) = CallPredicate(callTerm, type)
    fun getCall(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State()) = CallPredicate(lhv, callTerm, type)

    fun getStore(`object`: Term, storeTerm: Term, type: PredicateType = PredicateType.State()) = StorePredicate(`object`, storeTerm, type)
    fun getLoad(lhv: Term, loadTerm: Term) = getEquality(lhv, loadTerm)

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

    fun getEquality(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State()) = EqualityPredicate(lhv, rhv, type)

    fun getBoolean(lhv: Term, rhv: Term) = getEquality(lhv, rhv, PredicateType.Path())

    fun getDefaultSwitchPredicate(cond: Term, cases: Array<Term>, type: PredicateType = PredicateType.State())
            = DefaultSwitchPredicate(cond, cases, type)
}