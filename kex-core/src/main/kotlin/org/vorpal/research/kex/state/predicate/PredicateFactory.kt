package org.vorpal.research.kex.state.predicate

import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

object PredicateFactory {
    fun getBoundStore(
        ptr: Term,
        bound: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = BoundStorePredicate(ptr, bound, type, location)

    fun getCall(callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
        CallPredicate(callTerm, type, location)

    fun getCall(
        lhv: Term,
        callTerm: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = CallPredicate(lhv, callTerm, type, location)

    fun getArrayInitializer(
        arrayRef: Term,
        value: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = ArrayInitializerPredicate(arrayRef, value, type, location)

    fun getArrayStore(
        arrayRef: Term,
        value: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = ArrayStorePredicate(arrayRef, value, type, location)

    fun getFieldInitializer(
        field: Term,
        value: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = FieldInitializerPredicate(field, value, type, location)

    fun getFieldStore(
        field: Term,
        value: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = FieldStorePredicate(field, value, type, location)

    fun getGenerateArray(
        lhv: Term,
        length: Term,
        generator: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = GenerateArrayPredicate(lhv, length, generator, type, location)

    @Suppress("unused")
    fun getLoad(lhv: Term, loadTerm: Term, location: Location = Location()) =
        getEquality(lhv, loadTerm, location = location)

    fun getNew(lhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
        NewPredicate(lhv, type, location)

    fun getNewArray(
        lhv: Term,
        dimensions: List<Term>,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ): Predicate {
        var current = lhv.type
        dimensions.forEach { _ ->
            current = (current as? KexArray)?.element
                ?: unreachable { log.error("Trying to create new array predicate with non-array type") }
        }
        return NewArrayPredicate(lhv, dimensions, current, type, location)
    }

    fun getNewInitializer(lhv: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
        NewInitializerPredicate(lhv, type, location)

    fun getNewArrayInitializer(
        lhv: Term,
        length: Term,
        elements: List<Term>,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ): Predicate {
        return NewArrayInitializerPredicate(lhv, length, elements, (lhv.type as KexArray).element, type, location)
    }

    fun getEquality(
        lhv: Term,
        rhv: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = EqualityPredicate(lhv, rhv, type, location)

    fun getInequality(
        lhv: Term,
        rhv: Term,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = InequalityPredicate(lhv, rhv, type, location)

    @Suppress("unused")
    fun getBoolean(lhv: Term, rhv: Term, location: Location = Location()) =
        getEquality(lhv, rhv, PredicateType.Path(), location)

    fun getDefaultSwitchPredicate(
        cond: Term,
        cases: List<Term>,
        type: PredicateType = PredicateType.State(),
        location: Location = Location()
    ) = DefaultSwitchPredicate(cond, cases, type, location)

    fun getCatch(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
        CatchPredicate(throwable, type, location)

    fun getThrow(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
        ThrowPredicate(throwable, type, location)

    fun getEnterMonitor(monitor: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
        EnterMonitorPredicate(monitor, type, location)

    fun getExitMonitor(monitor: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) =
        ExitMonitorPredicate(monitor, type, location)
}

interface PredicateBuilder : TermBuilder {
    val type: PredicateType
    val location: Location

    val predicateFactory get() = PredicateFactory

    fun Term.initialize(other: Term) = when (this) {
        is ArrayIndexTerm -> predicateFactory.getArrayInitializer(this, other, this@PredicateBuilder.type, location)
        is FieldTerm -> predicateFactory.getFieldInitializer(this, other, this@PredicateBuilder.type, location)
        else -> unreachable { log.error("Trying to initialize unknown term: $this") }
    }

    fun Term.store(other: Term) = when (this) {
        is ArrayIndexTerm -> predicateFactory.getArrayStore(this, other, this@PredicateBuilder.type, location)
        is FieldTerm -> predicateFactory.getFieldStore(this, other, this@PredicateBuilder.type, location)
        else -> unreachable { log.error("Trying to store to unknown term: $this") }
    }

    fun Term.bound(other: Term) = predicateFactory.getBoundStore(this, other, this@PredicateBuilder.type, location)

    fun call(call: Term) = predicateFactory.getCall(call, this@PredicateBuilder.type, location)
    fun Term.call(callTerm: Term) = predicateFactory.getCall(this, callTerm, this@PredicateBuilder.type, location)

    fun catch(throwable: Term) = predicateFactory.getCatch(throwable, this@PredicateBuilder.type, location)
    fun `throw`(throwable: Term) = predicateFactory.getThrow(throwable, this@PredicateBuilder.type, location)

    fun enterMonitor(monitor: Term): Predicate =
        predicateFactory.getEnterMonitor(monitor, this@PredicateBuilder.type, location)

    fun exitMonitor(monitor: Term): Predicate =
        predicateFactory.getExitMonitor(monitor, this@PredicateBuilder.type, location)

    fun generateArray(lhv: Term, length: Term, generator: Term) =
        predicateFactory.getGenerateArray(lhv, length, generator, type, location)

    fun generateArray(lhv: Term, length: Term, builder: TermBuilder.() -> Term) =
        predicateFactory.getGenerateArray(lhv, length, builder(), type, location)

    @Suppress("FunctionName")
    infix fun Term.`!in`(cases: List<Term>) =
        predicateFactory.getDefaultSwitchPredicate(this, cases, this@PredicateBuilder.type, location)

    infix fun Term.equality(other: Term) =
        predicateFactory.getEquality(this, other, this@PredicateBuilder.type, location)

    infix fun Term.equality(other: Nothing?) =
        predicateFactory.getEquality(this, termFactory.getNull(), this@PredicateBuilder.type, location)

    infix fun <T : Number> Term.equality(rhv: T) =
        predicateFactory.getEquality(this, const(rhv), this@PredicateBuilder.type, location)

    infix fun Term.equality(rhv: Char) =
        predicateFactory.getEquality(this, const(rhv), this@PredicateBuilder.type, location)

    infix fun Nothing?.equality(other: Term) =
        predicateFactory.getEquality(termFactory.getNull(), other, this@PredicateBuilder.type, location)

    infix fun Term.equality(other: Boolean) =
        predicateFactory.getEquality(this, termFactory.getBool(other), this@PredicateBuilder.type, location)

    infix fun Boolean.equality(other: Term) =
        predicateFactory.getEquality(termFactory.getBool(this), other, this@PredicateBuilder.type, location)

    infix fun Boolean.equality(other: Boolean) =
        predicateFactory.getEquality(
            termFactory.getBool(this),
            termFactory.getBool(other),
            this@PredicateBuilder.type,
            location
        )

    infix fun Term.inequality(other: Term) =
        predicateFactory.getInequality(this, other, this@PredicateBuilder.type, location)

    infix fun <T : Number> Term.inequality(rhv: T) =
        predicateFactory.getInequality(this, const(rhv), this@PredicateBuilder.type, location)

    infix fun Term.inequality(other: Nothing?) =
        predicateFactory.getInequality(this, termFactory.getNull(), this@PredicateBuilder.type, location)

    infix fun Nothing?.inequality(other: Term) =
        predicateFactory.getInequality(termFactory.getNull(), other, this@PredicateBuilder.type, location)

    infix fun Term.inequality(other: Boolean) =
        predicateFactory.getInequality(this, termFactory.getBool(other), this@PredicateBuilder.type, location)

    infix fun Boolean.inequality(other: Term) =
        predicateFactory.getInequality(termFactory.getBool(this), other, this@PredicateBuilder.type, location)

    infix fun Boolean.inequality(other: Boolean) =
        predicateFactory.getInequality(
            termFactory.getBool(this),
            termFactory.getBool(other),
            this@PredicateBuilder.type,
            location
        )

    fun Term.new() = predicateFactory.getNew(this, this@PredicateBuilder.type, location)
    fun Term.new(dimensions: List<Term>) =
        predicateFactory.getNewArray(this, dimensions, this@PredicateBuilder.type, location)

    fun Term.new(vararg dimensions: Term) = this.new(dimensions.toList())
    fun Term.new(vararg dimensions: Int) = this.new(dimensions.map { const(it) })

    fun Term.initializeNew() = predicateFactory.getNewInitializer(this, this@PredicateBuilder.type, location)
    fun Term.initializeNew(length: Term, elements: List<Term>) =
        predicateFactory.getNewArrayInitializer(this, length, elements, this@PredicateBuilder.type, location)

    fun Term.initializeNew(length: Int, elements: List<Term>) =
        predicateFactory.getNewArrayInitializer(this, const(length), elements, this@PredicateBuilder.type, location)

    fun Term.initializeNew(length: Term, vararg elements: Term) = this.initializeNew(length, elements.toList())
    fun Term.initializeNew(length: Term, vararg elements: Number) =
        this.initializeNew(length, elements.map { const(it) })

    fun Term.initializeNew(length: Int, vararg elements: Number) =
        this.initializeNew(const(length), elements.map { const(it) })

    fun Term.initializeNew(length: Term, vararg elements: Char) = this.initializeNew(length, elements.map { const(it) })
    fun Term.initializeNew(length: Int, vararg elements: Char) =
        this.initializeNew(const(length), elements.map { const(it) })


    class Assume(override val location: Location = Location()) : PredicateBuilder {
        override val type get() = PredicateType.Assume()
    }

    class Axiom(override val location: Location = Location()) : PredicateBuilder {
        override val type get() = PredicateType.Axiom()
    }

    class State(override val location: Location = Location()) : PredicateBuilder {
        override val type get() = PredicateType.State()
    }

    class Path(override val location: Location = Location()) : PredicateBuilder {
        override val type get() = PredicateType.Path()
    }

    class Require(override val location: Location = Location()) : PredicateBuilder {
        override val type get() = PredicateType.Require()
    }

    fun term(body: TermBuilder.() -> Term) = this.body()
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
inline fun require(location: Location, body: PredicateBuilder.() -> Predicate) =
    PredicateBuilder.Require(location).body()

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
