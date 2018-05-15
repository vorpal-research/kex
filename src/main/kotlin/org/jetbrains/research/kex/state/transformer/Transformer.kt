package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChainState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable

interface Transformer<T : Transformer<T>> : Loggable {
    val pf: PredicateFactory get() = PredicateFactory
    val tf: TermFactory get() = TermFactory

    private fun delegatePredicate(argument: Predicate): Predicate {
        val `class` = Predicate.reverse.getValue(argument.javaClass)
        val argType = Predicate.predicates.getValue(`class`)
        val transformName = this.javaClass.getDeclaredMethod("transform$`class`", argType)
        val res = transformName.invoke(this, argument) as? Predicate
                ?: unreachable { log.debug("Unexpected null in transformer invocation ") }

        val transformClass = this.javaClass.getDeclaredMethod("transform$`class`Predicate", argType)
        return transformClass.invoke(this, res) as? Predicate
                ?: unreachable { log.debug("Unexpected null in transformer invocation ") }
    }

    private fun delegateTerm(argument: Term): Term {
        val `class` = Term.reverse.getValue(argument.javaClass)
        val argType = Term.terms.getValue(`class`)
        val transformName = this.javaClass.getDeclaredMethod("transform$`class`", argType)
        val res = transformName.invoke(this, argument) as? Term
                ?: unreachable { log.debug("Unexpected null in transformer invocation ") }

        val transformClass = this.javaClass.getDeclaredMethod("transform$`class`Term", argType)
        return transformClass.invoke(this, res) as? Term
                ?: unreachable { log.debug("Unexpected null in transformer invocation ") }
    }

    fun transform(ps: PredicateState) = transformBase(ps)
    fun transformBase(ps: PredicateState): PredicateState {
        val res = when (ps) {
            is BasicState -> transformBasic(ps)
            is ChainState -> transformChain(ps)
            is ChoiceState -> transformChoice(ps)
            else -> unreachable { log.debug("Unknown type of predicate state: $ps") }
        }
        return transformPredicateState(res)
    }

    fun transformPredicateState(ps: PredicateState) = ps

    fun transformBasic(ps: BasicState) = ps.map { p -> transformBase(p) }
    fun transformChain(ps: ChainState) = ps.fmap { e -> transformBase(e) }
    fun transformChoice(ps: ChoiceState) = ps.fmap { e -> transformBase(e) }

    fun transform(predicate: Predicate) = transformBase(predicate)
    fun transformBase(predicate: Predicate): Predicate {
        val res = delegatePredicate(predicate)
        return transformPredicate(res)
    }

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

    fun transformCallPredicate(predicate: CallPredicate) = predicate
    fun transformCatchPredicate(predicate: CatchPredicate) = predicate
    fun transformDefaultSwitchPredicate(predicate: DefaultSwitchPredicate) = predicate
    fun transformEqualityPredicate(predicate: EqualityPredicate) = predicate
    fun transformMultiNewArrayPredicate(predicate: MultiNewArrayPredicate) = predicate
    fun transformNewArrayPredicate(predicate: NewArrayPredicate) = predicate
    fun transformNewPredicate(predicate: NewPredicate) = predicate
    fun transformStorePredicate(predicate: StorePredicate) = predicate
    fun transformThrowPredicate(predicate: ThrowPredicate) = predicate

    fun transform(term: Term) = transformBase(term)
    fun transformBase(term: Term): Term {
        val res = delegateTerm(term)
        return transformTerm(res)
    }
    fun transformTerm(term: Term) = term

    fun transformArgument(term: ArgumentTerm) = term.accept(this)
    fun transformArrayLength(term: ArrayLengthTerm) = term.accept(this)
    fun transformArrayLoad(term: ArrayLoadTerm) = term.accept(this)
    fun transformBinary(term: BinaryTerm) = term.accept(this)
    fun transformCall(term: CallTerm) = term.accept(this)
    fun transformCast(term: CastTerm) = term.accept(this)
    fun transformCmp(term: CmpTerm) = term.accept(this)
    fun transformConstBool(term: ConstBoolTerm) = term.accept(this)
    fun transformConstByte(term: ConstByteTerm) = term.accept(this)
    fun transformConstChar(term: ConstCharTerm) = term.accept(this)
    fun transformConstClass(term: ConstClassTerm) = term.accept(this)
    fun transformConstDouble(term: ConstDoubleTerm) = term.accept(this)
    fun transformConstFloat(term: ConstFloatTerm) = term.accept(this)
    fun transformConstInt(term: ConstIntTerm) = term.accept(this)
    fun transformConstLong(term: ConstLongTerm) = term.accept(this)
    fun transformConstShort(term: ConstShortTerm) = term.accept(this)
    fun transformConstString(term: ConstStringTerm) = term.accept(this)
    fun transformFieldLoad(term: FieldLoadTerm) = term.accept(this)
    fun transformInstanceOf(term: InstanceOfTerm) = term.accept(this)
    fun transformNeg(term: NegTerm) = term.accept(this)
    fun transformNull(term: NullTerm) = term.accept(this)
    fun transformReturnValue(term: ReturnValueTerm) = term.accept(this)
    fun transformValue(term: ValueTerm) = term.accept(this)

    fun transformArgumentTerm(term: ArgumentTerm) = term
    fun transformArrayLengthTerm(term: ArrayLengthTerm) = term
    fun transformArrayLoadTerm(term: ArrayLoadTerm) = term
    fun transformBinaryTerm(term: BinaryTerm) = term
    fun transformCallTerm(term: CallTerm) = term
    fun transformCastTerm(term: CastTerm) = term
    fun transformCmpTerm(term: CmpTerm) = term
    fun transformConstBoolTerm(term: ConstBoolTerm) = term
    fun transformConstByteTerm(term: ConstByteTerm) = term
    fun transformConstCharTerm(term: ConstCharTerm) = term
    fun transformConstClassTerm(term: ConstClassTerm) = term
    fun transformConstDoubleTerm(term: ConstDoubleTerm) = term
    fun transformConstFloatTerm(term: ConstFloatTerm) = term
    fun transformConstIntTerm(term: ConstIntTerm) = term
    fun transformConstLongTerm(term: ConstLongTerm) = term
    fun transformConstShortTerm(term: ConstShortTerm) = term
    fun transformConstStringTerm(term: ConstStringTerm) = term
    fun transformFieldLoadTerm(term: FieldLoadTerm) = term
    fun transformInstanceOfTerm(term: InstanceOfTerm) = term
    fun transformNegTerm(term: NegTerm) = term
    fun transformNullTerm(term: NullTerm) = term
    fun transformReturnValueTerm(term: ReturnValueTerm) = term
    fun transformValueTerm(term: ValueTerm) = term
}