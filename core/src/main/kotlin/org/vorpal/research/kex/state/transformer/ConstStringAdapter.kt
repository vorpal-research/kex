package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.ktype.unreferenced
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.predicate.ArrayInitializerPredicate
import org.vorpal.research.kex.state.predicate.ArrayStorePredicate
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.FieldInitializerPredicate
import org.vorpal.research.kex.state.predicate.FieldStorePredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.predicate
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.CastTerm
import org.vorpal.research.kex.state.term.CmpTerm
import org.vorpal.research.kex.state.term.ConstStringTerm
import org.vorpal.research.kex.state.term.EqualsTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.InstanceOfTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.util.StringInfoContext
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.collection.dequeOf

class ConstStringAdapter(
    val tf: TypeFactory
) : StringInfoContext(), RecollectingTransformer<ConstStringAdapter>, IncrementalTransformer {
    override val builders = dequeOf(StateBuilder())
    private val strings = mutableMapOf<String, Term>()

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        val strings = buildSet {
            addAll(collectStringTerms(state.state))
            for (query in state.queries) {
                addAll(collectStringTerms(query.hardConstraints))
            }
        }
        return IncrementalPredicateState(
            apply(state.state, strings),
            state.queries
        )
    }

    private fun apply(ps: PredicateState, strings: Set<ConstStringTerm>): PredicateState {
        for (str in strings) {
            currentBuilder += buildStr(str.value)
        }
        return super.apply(ps)
    }

    override fun apply(ps: PredicateState): PredicateState {
        val strings = collectStringTerms(ps)
        return apply(ps, strings)
    }

    @Suppress("DuplicatedCode")
    private fun buildStr(string: String): PredicateState = basic {
        val strTerm = generate(KexString())
        state { strTerm.initializeNew() }

        val valueArray = generate(valueArrayType)
        state { valueArray.initializeNew(string.length, string.map { it.asType(valueArrayType.element) }) }
        state { strTerm.field(valueArrayType, valueArrayName).initialize(valueArray) }
        strings[string] = strTerm
    }

    private fun replaceString(constStringTerm: ConstStringTerm) =
        strings.getOrDefault(constStringTerm.value, constStringTerm)

    private val Term.map
        get() = when (this) {
            is ConstStringTerm -> replaceString(this)
            else -> this
        }

    override fun transformArrayInitializerPredicate(predicate: ArrayInitializerPredicate): Predicate =
        predicate(predicate.type, predicate.location) { predicate.arrayRef.initialize(predicate.value.map) }


    override fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate =
        predicate(predicate.type, predicate.location) { predicate.arrayRef.store(predicate.value.map) }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        return when {
            predicate.hasLhv -> predicate(
                predicate.type,
                predicate.location
            ) { predicate.lhv.map.call(predicate.callTerm) }
            else -> predicate(predicate.type, predicate.location) { call(predicate.callTerm) }
        }
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val newLhv = predicate.lhv.map
        val newRhv = predicate.rhv.map
        return predicate(predicate.type, predicate.location) { newLhv equality newRhv }
    }

    override fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate =
        predicate(predicate.type, predicate.location) { predicate.field.initialize(predicate.value.map) }


    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate =
        predicate(predicate.type, predicate.location) { predicate.field.store(predicate.value.map) }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        val newLhv = predicate.lhv.map
        val newRhv = predicate.rhv.map
        return predicate(predicate.type, predicate.location) { newLhv inequality newRhv }
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term =
        term { term.lhv.map.apply(term.type, term.opcode, term.rhv.map) }

    override fun transformCallTerm(term: CallTerm): Term {
        val args = term.arguments.map { it.map }
        val owner = term.owner.map
        return term { owner.call(term.method, args) }
    }

    override fun transformCastTerm(term: CastTerm): Term = term { term.operand.map `as` term.type }

    override fun transformCmpTerm(term: CmpTerm): Term =
        term { term.lhv.map.apply(term.opcode, term.rhv.map) }

    override fun transformFieldTerm(term: FieldTerm): Term {
        val owner = term.owner.map
        return term { owner.field(term.type as KexReference, term.fieldName) }
    }

    override fun transformInstanceOf(term: InstanceOfTerm): Term {
        return term { term.operand.map `is` term.checkedType }
    }

    override fun transformEquals(term: EqualsTerm): Term {
        return term { term.lhv.map equls term.rhv.map }
    }

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        return term { lambda(term.type, term.parameters, transform(term.body)) }
    }
}

class TypeNameAdapter(
    val ctx: ExecutionContext
) : StringInfoContext(), RecollectingTransformer<TypeNameAdapter>, IncrementalTransformer {
    override val builders = dequeOf(StateBuilder())

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        if (!hasClassAccesses(state)) return state

        val constStrings = getConstStringMap(state)
        val strings = collectTypes(ctx, state)
            .map { it.unreferenced() }
            .map { term { const(it.javaName) } as ConstStringTerm }
            .toMutableSet()
        if (strings.isNotEmpty()) {
            strings += term { const(KexString().javaName) } as ConstStringTerm
            strings += term { const(valueArrayType.javaName) } as ConstStringTerm
            strings += term { const(valueArrayType.element.javaName) } as ConstStringTerm
            strings += term { const(KexInt.javaName) } as ConstStringTerm
        }

        val newState = StateBuilder()
        for (str in strings.filter { it.value !in constStrings }) {
            newState += buildStr(str.value)
        }
        newState += state.state
        return IncrementalPredicateState(
            newState.apply(),
            state.queries
        )
    }

    override fun apply(ps: PredicateState): PredicateState {
        if (!hasClassAccesses(ps)) return ps

        val constStrings = getConstStringMap(ps)
        val strings = collectTypes(ctx, ps)
            .map { it.unreferenced() }
            .map { term { const(it.javaName) } as ConstStringTerm }
            .toMutableSet()
        if (strings.isNotEmpty()) {
            strings += term { const(KexString().javaName) } as ConstStringTerm
            strings += term { const(valueArrayType.javaName) } as ConstStringTerm
            strings += term { const(valueArrayType.element.javaName) } as ConstStringTerm
            strings += term { const(KexInt.javaName) } as ConstStringTerm
        }

        for (str in strings.filter { it.value !in constStrings }) {
            currentBuilder += buildStr(str.value)
        }
        return super.apply(ps)
    }

    @Suppress("DuplicatedCode")
    private fun buildStr(string: String): PredicateState = basic {
        val strTerm = generate(KexString())
        state { strTerm.initializeNew() }

        val valueArray = generate(valueArrayType)
        state { valueArray.initializeNew(string.length, string.map { it.asType(valueArrayType.element) }) }
        state { strTerm.field(valueArrayType, valueArrayName).initialize(valueArray) }
    }
}
