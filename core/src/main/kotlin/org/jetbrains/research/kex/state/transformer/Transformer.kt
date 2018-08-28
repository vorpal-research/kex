package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable

interface Transformer<T : Transformer<T>> {
    val pf: PredicateFactory
        get() = PredicateFactory

    val tf: TermFactory
        get() = TermFactory

    private inline fun < reified T : TypeInfo> delegate(argument: T, type: String): T {
        val subtypeName = argument.reverseMapping.getValue(argument.javaClass)
        val subtype = argument.subtypes.getValue(subtypeName)
        val transformName = this.javaClass.getDeclaredMethod("transform$subtypeName", subtype)
        val res = transformName.invoke(this, argument) as? T
                ?: unreachable { log.debug("Unexpected null in transformer invocation") }

        val transformClass = this.javaClass.getDeclaredMethod("transform$subtypeName$type", subtype)
        return transformClass.invoke(this, res) as? T
                ?: unreachable { log.debug("Unexpected null in transformer invocation") }
    }

    fun apply(ps: PredicateState) = transform(ps)

    ////////////////////////////////////////////////////////////////////
    // PredicateState
    ////////////////////////////////////////////////////////////////////
    fun transform(ps: PredicateState) = transformBase(ps)

    fun transformBase(ps: PredicateState): PredicateState {
        val res = delegate(ps, "State")
        return transformPredicateState(res)
    }

    fun transformPredicateState(ps: PredicateState) = ps

    fun transformBasic(ps: BasicState): PredicateState = ps.map { p -> transformBase(p) }
    fun transformChain(ps: ChainState): PredicateState = ps.fmap { e -> transformBase(e) }
    fun transformChoice(ps: ChoiceState): PredicateState = ps.fmap { e -> transformBase(e) }

    fun transformBasicState(ps: BasicState): PredicateState = ps
    fun transformChainState(ps: ChainState): PredicateState = ps
    fun transformChoiceState(ps: ChoiceState): PredicateState = ps

    fun transform(predicate: Predicate) = transformBase(predicate)
    fun transformBase(predicate: Predicate): Predicate {
        val res = delegate(predicate, "Predicate")
        return transformPredicate(res)
    }

    ////////////////////////////////////////////////////////////////////
    // Predicate
    ////////////////////////////////////////////////////////////////////
    fun transformPredicate(predicate: Predicate) = predicate

    fun transformArrayStore(predicate: ArrayStorePredicate) = predicate.accept(this)
    fun transformBoundStore(predicate: BoundStorePredicate) = predicate.accept(this)
    fun transformCall(predicate: CallPredicate) = predicate.accept(this)
    fun transformCatch(predicate: CatchPredicate) = predicate.accept(this)
    fun transformDefaultSwitch(predicate: DefaultSwitchPredicate) = predicate.accept(this)
    fun transformInequality(predicate: InequalityPredicate) = predicate.accept(this)
    fun transformEquality(predicate: EqualityPredicate) = predicate.accept(this)
    fun transformFieldStore(predicate: FieldStorePredicate) = predicate.accept(this)
    fun transformNewArray(predicate: NewArrayPredicate) = predicate.accept(this)
    fun transformNew(predicate: NewPredicate) = predicate.accept(this)
    fun transformThrow(predicate: ThrowPredicate) = predicate.accept(this)

    fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate = predicate
    fun transformBoundStorePredicate(predicate: BoundStorePredicate) = predicate
    fun transformCallPredicate(predicate: CallPredicate): Predicate = predicate
    fun transformCatchPredicate(predicate: CatchPredicate): Predicate = predicate
    fun transformDefaultSwitchPredicate(predicate: DefaultSwitchPredicate): Predicate = predicate
    fun transformInequalityPredicate(predicate: InequalityPredicate) = predicate
    fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate = predicate
    fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate = predicate
    fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate = predicate
    fun transformNewPredicate(predicate: NewPredicate): Predicate = predicate
    fun transformThrowPredicate(predicate: ThrowPredicate): Predicate = predicate

    ////////////////////////////////////////////////////////////////////
    // Term
    ////////////////////////////////////////////////////////////////////
    fun transform(term: Term) = transformBase(term)

    fun transformBase(term: Term): Term {
        val res = delegate(term, "Term")
        return transformTerm(res)
    }

    fun transformTerm(term: Term) = term

    fun transformArgument(term: ArgumentTerm): Term = term.accept(this)
    fun transformArrayIndex(term: ArrayIndexTerm): Term = term.accept(this)
    fun transformArrayLength(term: ArrayLengthTerm): Term = term.accept(this)
    fun transformArrayLoad(term: ArrayLoadTerm): Term = term.accept(this)
    fun transformBinary(term: BinaryTerm): Term = term.accept(this)
    fun transformBound(term: BoundTerm): Term = term.accept(this)
    fun transformCall(term: CallTerm): Term = term.accept(this)
    fun transformCast(term: CastTerm): Term = term.accept(this)
    fun transformCmp(term: CmpTerm): Term = term.accept(this)
    fun transformConstBool(term: ConstBoolTerm): Term = term.accept(this)
    fun transformConstByte(term: ConstByteTerm): Term = term.accept(this)
    fun transformConstChar(term: ConstCharTerm): Term = term.accept(this)
    fun transformConstClass(term: ConstClassTerm): Term = term.accept(this)
    fun transformConstDouble(term: ConstDoubleTerm): Term = term.accept(this)
    fun transformConstFloat(term: ConstFloatTerm): Term = term.accept(this)
    fun transformConstInt(term: ConstIntTerm): Term = term.accept(this)
    fun transformConstLong(term: ConstLongTerm): Term = term.accept(this)
    fun transformConstShort(term: ConstShortTerm): Term = term.accept(this)
    fun transformConstString(term: ConstStringTerm): Term = term.accept(this)
    fun transformFieldLoad(term: FieldLoadTerm): Term = term.accept(this)
    fun transformField(term: FieldTerm): Term = term.accept(this)
    fun transformInstanceOf(term: InstanceOfTerm): Term = term.accept(this)
    fun transformNeg(term: NegTerm): Term = term.accept(this)
    fun transformNull(term: NullTerm): Term = term.accept(this)
    fun transformReturnValue(term: ReturnValueTerm): Term = term.accept(this)
    fun transformValue(term: ValueTerm): Term = term.accept(this)

    fun transformArgumentTerm(term: ArgumentTerm): Term = term
    fun transformArrayIndexTerm(term: ArrayIndexTerm): Term = term
    fun transformArrayLengthTerm(term: ArrayLengthTerm): Term = term
    fun transformArrayLoadTerm(term: ArrayLoadTerm): Term = term
    fun transformBinaryTerm(term: BinaryTerm): Term = term
    fun transformBoundTerm(term: BoundTerm): Term = term
    fun transformCallTerm(term: CallTerm): Term = term
    fun transformCastTerm(term: CastTerm): Term = term
    fun transformCmpTerm(term: CmpTerm): Term = term
    fun transformConstBoolTerm(term: ConstBoolTerm): Term = term
    fun transformConstByteTerm(term: ConstByteTerm): Term = term
    fun transformConstCharTerm(term: ConstCharTerm): Term = term
    fun transformConstClassTerm(term: ConstClassTerm): Term = term
    fun transformConstDoubleTerm(term: ConstDoubleTerm): Term = term
    fun transformConstFloatTerm(term: ConstFloatTerm): Term = term
    fun transformConstIntTerm(term: ConstIntTerm): Term = term
    fun transformConstLongTerm(term: ConstLongTerm): Term = term
    fun transformConstShortTerm(term: ConstShortTerm): Term = term
    fun transformConstStringTerm(term: ConstStringTerm): Term = term
    fun transformFieldLoadTerm(term: FieldLoadTerm): Term = term
    fun transformFieldTerm(term: FieldTerm): Term = term
    fun transformInstanceOfTerm(term: InstanceOfTerm): Term = term
    fun transformNegTerm(term: NegTerm): Term = term
    fun transformNullTerm(term: NullTerm): Term = term
    fun transformReturnValueTerm(term: ReturnValueTerm): Term = term
    fun transformValueTerm(term: ValueTerm): Term = term
}


interface DeletingTransformer<T> : Transformer<DeletingTransformer<T>> {
    val removablePredicates: MutableSet<Predicate>

    override fun apply(ps: PredicateState): PredicateState {
        val result = super.transform(ps)
        return result.filter { it in removablePredicates }.simplify()
    }
}