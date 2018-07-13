package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.Type

object PredicateFactory : Loggable {
    fun getCall(callTerm: Term, type: PredicateType = PredicateType.State()) = CallPredicate(callTerm, type)
    fun getCall(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State()) = CallPredicate(lhv, callTerm, type)

    fun getArrayStore(arrayRef: Term, value: Term, type: PredicateType = PredicateType.State())
            = ArrayStorePredicate(arrayRef, value, type)

    fun getFieldStore(field: Term, fieldType: Type, value: Term, type: PredicateType = PredicateType.State()) =
            FieldStorePredicate(field, fieldType, value, type)

    fun getLoad(lhv: Term, loadTerm: Term) = getEquality(lhv, loadTerm)

    fun getNew(lhv: Term, type: PredicateType = PredicateType.State()) = NewPredicate(lhv, type)
    fun getNewArray(lhv: Term, dimensions: List<Term>, type: PredicateType = PredicateType.State()): Predicate {
        var current = lhv.type
        dimensions.forEach {
            current = (current as? ArrayType
                    ?: error(log.error("Trying to create new array predicate with non-array type"))).component
        }
        return NewArrayPredicate(lhv, dimensions, current, type)
    }

    fun getEquality(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State()) = EqualityPredicate(lhv, rhv, type)

    fun getBoolean(lhv: Term, rhv: Term) = getEquality(lhv, rhv, PredicateType.Path())

    fun getDefaultSwitchPredicate(cond: Term, cases: List<Term>, type: PredicateType = PredicateType.State())
            = DefaultSwitchPredicate(cond, cases, type)

    fun getCatch(throwable: Term, type: PredicateType = PredicateType.State()) = CatchPredicate(throwable, type)
    fun getThrow(throwable: Term, type: PredicateType = PredicateType.State()) = ThrowPredicate(throwable, type)
}