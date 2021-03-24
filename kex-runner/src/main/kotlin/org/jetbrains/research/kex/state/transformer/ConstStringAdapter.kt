package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexChar
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*

class ConstStringAdapter : RecollectingTransformer<ConstStringAdapter> {
    override val builders = dequeOf(StateBuilder())
    val strings = mutableMapOf<String, Term>()

    override fun apply(ps: PredicateState): PredicateState {
        val strings = collectStringTerms(ps)
        for (str in strings) {
            currentBuilder += buildStr(str.value)
        }
        return super.apply(ps)
    }

    private fun buildStr(string: String): PredicateState = StateBuilder().apply {
        val strTerm = generate(KexClass("java/lang/String"))
        state { strTerm.new() }

        val charArray = KexArray(KexChar())
        val valueArray = generate(charArray)
        state { valueArray.new(string.length) }
        for ((index, char) in string.withIndex()) {
            state { valueArray[index].store(const(char)) }
        }

        state { strTerm.field(charArray, "value").store(valueArray) }
        strings[string] = strTerm
    }.apply()

    private fun replaceString(constStringTerm: ConstStringTerm) = strings.getOrDefault(constStringTerm.value, constStringTerm)

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
            predicate.hasLhv -> predicate(predicate.type, predicate.location) { predicate.lhv.map.call(predicate.callTerm) }
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
}