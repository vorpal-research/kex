package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable

interface Transformer<T : Transformer<T>> : Loggable {
    val pf: PredicateFactory get() = PredicateFactory
    val tf: TermFactory get() = TermFactory

    @Suppress("UNCHECKED_CAST")
    private fun <T : Sealed> delegate(argument: T, type: String): T {
        val `class` = argument.getReverseMapping().getValue(argument.javaClass)
        val argType = argument.getSubtypes().getValue(`class`)
        val transformName = this.javaClass.getDeclaredMethod("transform$`class`", argType)
        val res = transformName.invoke(this, argument) as? T
                ?: unreachable { log.debug("Unexpected null in transformer invocation ") }

        val transformClass = this.javaClass.getDeclaredMethod("transform$`class`$type", argType)
        return transformClass.invoke(this, res) as? T
                ?: unreachable { log.debug("Unexpected null in transformer invocation ") }
    }

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

    fun transformCall(predicate: CallPredicate) = predicate.accept(this)
    fun transformCatch(predicate: CatchPredicate) = predicate.accept(this)
    fun transformDefaultSwitch(predicate: DefaultSwitchPredicate) = predicate.accept(this)
    fun transformEquality(predicate: EqualityPredicate) = predicate.accept(this)
    fun transformMultiNewArray(predicate: MultiNewArrayPredicate) = predicate.accept(this)
    fun transformNewArray(predicate: NewArrayPredicate) = predicate.accept(this)
    fun transformNew(predicate: NewPredicate) = predicate.accept(this)
    fun transformStore(predicate: StorePredicate) = predicate.accept(this)
    fun transformThrow(predicate: ThrowPredicate) = predicate.accept(this)

    fun transformCallPredicate(predicate: CallPredicate): Predicate = predicate
    fun transformCatchPredicate(predicate: CatchPredicate): Predicate = predicate
    fun transformDefaultSwitchPredicate(predicate: DefaultSwitchPredicate): Predicate = predicate
    fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate = predicate
    fun transformMultiNewArrayPredicate(predicate: MultiNewArrayPredicate): Predicate = predicate
    fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate = predicate
    fun transformNewPredicate(predicate: NewPredicate): Predicate = predicate
    fun transformStorePredicate(predicate: StorePredicate): Predicate = predicate
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
    fun transformArrayLength(term: ArrayLengthTerm): Term = term.accept(this)
    fun transformArrayLoad(term: ArrayLoadTerm): Term = term.accept(this)
    fun transformBinary(term: BinaryTerm): Term = term.accept(this)
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
    fun transformInstanceOf(term: InstanceOfTerm): Term = term.accept(this)
    fun transformNeg(term: NegTerm): Term = term.accept(this)
    fun transformNull(term: NullTerm): Term = term.accept(this)
    fun transformReturnValue(term: ReturnValueTerm): Term = term.accept(this)
    fun transformValue(term: ValueTerm): Term = term.accept(this)

    fun transformArgumentTerm(term: ArgumentTerm): Term = term
    fun transformArrayLengthTerm(term: ArrayLengthTerm): Term = term
    fun transformArrayLoadTerm(term: ArrayLoadTerm): Term = term
    fun transformBinaryTerm(term: BinaryTerm): Term = term
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
    fun transformInstanceOfTerm(term: InstanceOfTerm): Term = term
    fun transformNegTerm(term: NegTerm): Term = term
    fun transformNullTerm(term: NullTerm): Term = term
    fun transformReturnValueTerm(term: ReturnValueTerm): Term = term
    fun transformValueTerm(term: ValueTerm): Term = term
}