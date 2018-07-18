package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.type.Type

object PredicateFactory : Loggable {
    fun getCall(callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CallPredicate(callTerm, type, location)

    fun getCall(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CallPredicate(lhv, callTerm, type)

    fun getArrayStore(arrayRef: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            ArrayStorePredicate(arrayRef, value, type, location)

    fun getFieldStore(field: Term, fieldType: Type, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            FieldStorePredicate(field, fieldType, value, type, location)

    fun getLoad(lhv: Term, loadTerm: Term, location: Location = Location()) = getEquality(lhv, loadTerm, location = location)

    fun getNew(lhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            NewPredicate(lhv, type, location)
    fun getNewArray(lhv: Term, dimensions: List<Term>, type: PredicateType = PredicateType.State(), location: Location = Location()): Predicate {
        var current = lhv.type
        dimensions.forEach {
            current = (current as? ArrayType
                    ?: error(log.error("Trying to create new array predicate with non-array type"))).component
        }
        return NewArrayPredicate(lhv, dimensions, current, type, location)
    }

    fun getEquality(lhv: Term, rhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            EqualityPredicate(lhv, rhv, type, location)

    fun getBoolean(lhv: Term, rhv: Term, location: Location = Location()) =
            getEquality(lhv, rhv, PredicateType.Path(), location)

    fun getDefaultSwitchPredicate(cond: Term, cases: List<Term>, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            DefaultSwitchPredicate(cond, cases, type, location)

    fun getCatch(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CatchPredicate(throwable, type, location)

    fun getThrow(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            ThrowPredicate(throwable, type, location)
}