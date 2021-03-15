package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.state.term.ArrayIndexTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermBuilder
import org.jetbrains.research.kfg.ir.Location

object PredicateFactory {
    fun getBoundStore(ptr: Term, bound: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            BoundStorePredicate(ptr, bound, type, location)

    fun getCall(callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CallPredicate(callTerm, type, location)

    fun getCall(lhv: Term, callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            CallPredicate(lhv, callTerm, type, location)

    fun getArrayInitializer(arrayRef: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            ArrayInitializerPredicate(arrayRef, value, type, location)

    fun getArrayStore(arrayRef: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            ArrayStorePredicate(arrayRef, value, type, location)

    fun getFieldInitializer(field: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
            FieldInitializerPredicate(field, value, type, location)

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

abstract class PredicateBuilder : TermBuilder() {
    abstract val type: PredicateType
    abstract val location: Location

    val pf = PredicateFactory

    fun Term.initialize(other: Term) = when (this) {
        is ArrayIndexTerm -> pf.getArrayInitializer(this, other, this@PredicateBuilder.type, location)
        is FieldTerm -> pf.getFieldInitializer(this, other, this@PredicateBuilder.type, location)
        else -> unreachable { log.error("Trying to initialize unknown term: $this") }
    }

    fun Term.store(other: Term) = when (this) {
        is ArrayIndexTerm -> pf.getArrayStore(this, other, this@PredicateBuilder.type, location)
        is FieldTerm -> pf.getFieldStore(this, other, this@PredicateBuilder.type, location)
        else -> unreachable { log.error("Trying to store to unknown term: $this") }
    }

    fun Term.bound(other: Term) = pf.getBoundStore(this, other, this@PredicateBuilder.type, location)

    fun call(call: Term) = pf.getCall(call, this@PredicateBuilder.type, location)
    fun Term.call(callTerm: Term) = pf.getCall(this, callTerm, this@PredicateBuilder.type, location)

    fun catch(throwable: Term) = pf.getCatch(throwable, this@PredicateBuilder.type, location)
    fun `throw`(throwable: Term) = pf.getThrow(throwable, this@PredicateBuilder.type, location)

    infix fun Term.`!in`(cases: List<Term>) = pf.getDefaultSwitchPredicate(this, cases, this@PredicateBuilder.type, location)

    infix fun Term.equality(other: Term) =
            pf.getEquality(this, other, this@PredicateBuilder.type, location)
    infix fun Term.equality(@Suppress("UNUSED_PARAMETER") other: Nothing?) =
            pf.getEquality(this, tf.getNull(), this@PredicateBuilder.type, location)
    infix fun <T : Number> Term.equality(rhv: T) =
            pf.getEquality(this, const(rhv), this@PredicateBuilder.type, location)
    infix fun Nothing?.equality(other: Term) =
            pf.getEquality(tf.getNull(), other, this@PredicateBuilder.type, location)
    infix fun Term.equality(other: Boolean) =
            pf.getEquality(this, tf.getBool(other), this@PredicateBuilder.type, location)
    infix fun Boolean.equality(other: Term) =
            pf.getEquality(tf.getBool(this), other, this@PredicateBuilder.type, location)

    infix fun Term.inequality(other: Term) =
            pf.getInequality(this, other, this@PredicateBuilder.type, location)
    infix fun <T : Number> Term.inequality(rhv: T) =
            pf.getInequality(this, const(rhv), this@PredicateBuilder.type, location)
    infix fun Term.inequality(@Suppress("UNUSED_PARAMETER") other: Nothing?) =
            pf.getInequality(this, tf.getNull(), this@PredicateBuilder.type, location)
    infix fun Nothing?.inequality(other: Term) =
            pf.getInequality(tf.getNull(), other, this@PredicateBuilder.type, location)
    infix fun Term.inequality(other: Boolean) =
            pf.getInequality(this, tf.getBool(other), this@PredicateBuilder.type, location)
    infix fun Boolean.inequality(other: Term) =
            pf.getInequality(tf.getBool(this), other, this@PredicateBuilder.type, location)

    fun Term.new() = pf.getNew(this, this@PredicateBuilder.type, location)
    fun Term.new(dimensions: List<Term>) = pf.getNewArray(this, dimensions, this@PredicateBuilder.type, location)
    fun Term.new(vararg dimensions: Term) = this.new(dimensions.toList())
    fun Term.new(vararg dimensions: Int) = this.new(dimensions.map { const(it) })

    class Assume(override val location: Location = Location()) : PredicateBuilder() {
        override val type get() = PredicateType.Assume()
    }
    class Axiom(override val location: Location = Location()) : PredicateBuilder() {
        override val type get() = PredicateType.Axiom()
    }
    class State(override val location: Location = Location()) : PredicateBuilder() {
        override val type get() = PredicateType.State()
    }
    class Path(override val location: Location = Location()) : PredicateBuilder() {
        override val type get() = PredicateType.Path()
    }
    class Require(override val location: Location = Location()) : PredicateBuilder() {
        override val type get() = PredicateType.Require()
    }

    inline fun term(body: TermBuilder.() -> Term) = this.body()
}

inline fun assume(body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Assume().body()
inline fun assume(location: Location, body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Assume(location).body()

inline fun axiom(body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Axiom().body()
inline fun axiom(location: Location, body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Axiom(location).body()

inline fun state(body: PredicateBuilder.() -> Predicate) = PredicateBuilder.State().body()
inline fun state(location: Location, body: PredicateBuilder.() -> Predicate) = PredicateBuilder.State(location).body()

inline fun path(body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Path().body()
inline fun path(location: Location, body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Path(location).body()

inline fun require(body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Require().body()
inline fun require(location: Location, body: PredicateBuilder.() -> Predicate) = PredicateBuilder.Require(location).body()

inline fun predicate(type: PredicateType, body: PredicateBuilder.() -> Predicate) = when (type) {
    is PredicateType.Assume -> assume(body)
    is PredicateType.Axiom -> axiom(body)
    is PredicateType.Require -> require(body)
    is PredicateType.State -> state(body)
    is PredicateType.Path -> path(body)
    else -> unreachable { log.error("Unknown predicate type $type") }
}

inline fun predicate(type: PredicateType, location: Location, body: PredicateBuilder.() -> Predicate) = when (type) {
    is PredicateType.Assume -> assume(location, body)
    is PredicateType.Axiom -> axiom(location, body)
    is PredicateType.Require -> require(location, body)
    is PredicateType.State -> state(location, body)
    is PredicateType.Path -> path(location, body)
    else -> unreachable { log.error("Unknown predicate type $type") }
}