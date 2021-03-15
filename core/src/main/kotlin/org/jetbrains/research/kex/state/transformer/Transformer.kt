package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.Location
import java.util.*

interface Transformer<T : Transformer<T>> {
    /**
     * Stub to return when you want to delete some predicate in predicate state
     * Needed to avoid using nullable types in transformer
     * Should *never* appear outside of transformers
     */
    private object Stub : Predicate() {
        override val type = PredicateType.State()
        override val location = Location()
        override val operands = listOf<Term>()

        override fun print() = "stub"
        override fun <T : Transformer<T>> accept(t: Transformer<T>) = Stub
    }

    fun nothing(): Predicate = Stub

    private inline fun <reified T : TypeInfo> delegate(argument: T, type: String): T {
        // this is fucked up, but at least it's not nullable
        if (argument is Stub) return argument

        val subtypeName = argument.reverseMapping.getValue(argument.javaClass)
        val subtype = argument.inheritors.getValue(subtypeName)

        val transformName = this.javaClass.getDeclaredMethod("transform$subtypeName", subtype)

        val res = transformName.invoke(this, argument) as? T
                ?: unreachable { log.debug("Unexpected null in transformer invocation") }
        if (res is Stub) return res

        val newSubtypeName = res.reverseMapping.getValue(res.javaClass)
        val newSubtype = res.inheritors.getValue(newSubtypeName)
        val transformClass = this.javaClass.getDeclaredMethod("transform$newSubtypeName$type", newSubtype)
        return transformClass.invoke(this, res) as? T
                ?: unreachable { log.debug("Unexpected null in transformer invocation") }
    }

    fun apply(ps: PredicateState) = transform(ps).simplify()

    ////////////////////////////////////////////////////////////////////
    // PredicateState
    ////////////////////////////////////////////////////////////////////
    fun transform(ps: PredicateState) = transformBase(ps)

    fun transformBase(ps: PredicateState): PredicateState {
        val res = delegate(ps, "State")
        return transformPredicateState(res)
    }

    fun transformPredicateState(ps: PredicateState) = ps

    fun transformBasic(ps: BasicState): PredicateState = ps.map { p -> transformBase(p) }.filterNot { it is Stub }
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

    fun transformArrayInitializer(predicate: ArrayInitializerPredicate): Predicate = predicate.accept(this)
    fun transformArrayStore(predicate: ArrayStorePredicate): Predicate = predicate.accept(this)
    fun transformBoundStore(predicate: BoundStorePredicate): Predicate = predicate.accept(this)
    fun transformCall(predicate: CallPredicate): Predicate = predicate.accept(this)
    fun transformCatch(predicate: CatchPredicate): Predicate = predicate.accept(this)
    fun transformDefaultSwitch(predicate: DefaultSwitchPredicate): Predicate = predicate.accept(this)
    fun transformInequality(predicate: InequalityPredicate): Predicate = predicate.accept(this)
    fun transformEquality(predicate: EqualityPredicate): Predicate = predicate.accept(this)
    fun transformFieldInitializer(predicate: FieldInitializerPredicate): Predicate = predicate.accept(this)
    fun transformFieldStore(predicate: FieldStorePredicate): Predicate = predicate.accept(this)
    fun transformNewArray(predicate: NewArrayPredicate): Predicate = predicate.accept(this)
    fun transformNew(predicate: NewPredicate): Predicate = predicate.accept(this)
    fun transformThrow(predicate: ThrowPredicate): Predicate = predicate.accept(this)

    fun transformArrayInitializerPredicate(predicate: ArrayInitializerPredicate): Predicate = predicate
    fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate = predicate
    fun transformBoundStorePredicate(predicate: BoundStorePredicate): Predicate = predicate
    fun transformCallPredicate(predicate: CallPredicate): Predicate = predicate
    fun transformCatchPredicate(predicate: CatchPredicate): Predicate = predicate
    fun transformDefaultSwitchPredicate(predicate: DefaultSwitchPredicate): Predicate = predicate
    fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate = predicate
    fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate = predicate
    fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate = predicate
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
    fun transformUndef(term: UndefTerm): Term = term.accept(this)

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
    fun transformUndefTerm(term: UndefTerm): Term = term
}

interface RecollectingTransformer<T> : Transformer<RecollectingTransformer<T>> {
    val builders: Deque<StateBuilder>

    val currentBuilder: StateBuilder
        get() = builders.last

    val state: PredicateState
        get() = currentBuilder.apply()

    override fun apply(ps: PredicateState): PredicateState {
        super.transform(ps)
        return state.simplify()
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val newChoices = arrayListOf<PredicateState>()
        for (choice in ps.choices) {
            builders.add(StateBuilder())
            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
            builders.pollLast()
        }
        currentBuilder += newChoices
        return ps
    }

    override fun transformPredicate(predicate: Predicate): Predicate {
        val result = super.transformPredicate(predicate)
        if (result != nothing()) currentBuilder += result
        return result
    }
}