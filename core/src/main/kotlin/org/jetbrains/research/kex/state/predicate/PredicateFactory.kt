package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Location

object PredicateFactory {
    fun getBoundStore(ptr: Term, bound: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            BoundStorePredicate(ptr, bound, type, location)

    fun getCall(callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CallPredicate(callTerm, type, location)

    fun getCall(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CallPredicate(lhv, callTerm, type, location)

    fun getArrayStore(arrayRef: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            ArrayStorePredicate(arrayRef, value, type, location)

    fun getFieldStore(field: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            FieldStorePredicate(field, value, type, location)

    fun getLoad(lhv: Term, loadTerm: Term, location: Location = Location()) = getEquality(lhv, loadTerm, location = location)

    fun getNew(lhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            NewPredicate(lhv, type, location)

    fun getNewArray(lhv: Term, dimensions: List<Term>, type: PredicateType = PredicateType.State(), location: Location = Location()): Predicate {
        var current = lhv.type
        dimensions.forEach { _ ->
            current = (current as? KexArray)?.element ?: unreachable { log.error("Trying to create new array predicate with non-array type") }
        }
        return NewArrayPredicate(lhv, dimensions, current, type, location)
    }

    fun getEquality(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            EqualityPredicate(lhv, rhv, type, location)

    fun getInequality(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            InequalityPredicate(lhv, rhv, type, location)

    fun getBoolean(lhv: Term, rhv: Term, location: Location = Location()) =
            getEquality(lhv, rhv, PredicateType.Path(), location)

    fun getDefaultSwitchPredicate(cond: Term, cases: List<Term>, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            DefaultSwitchPredicate(cond, cases, type, location)

    fun getCatch(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CatchPredicate(throwable, type, location)

    fun getThrow(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            ThrowPredicate(throwable, type, location)
}