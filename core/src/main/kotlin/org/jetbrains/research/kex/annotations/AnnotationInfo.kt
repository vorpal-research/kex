package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChainState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import kotlin.reflect.full.findAnnotation

abstract class AnnotationInfo {

    open fun preciseParam(value: Term, n: Int): PredicateState? = null
    open fun preciseValue(value: Term): PredicateState? = null
    open fun preciseBeforeCall(predicate: CallPredicate): PredicateState? = null
    open fun preciseAfterCall(predicate: CallPredicate): PredicateState? = null
    open fun preciseReturn(value: Term): PredicateState? = null

    open fun initialize(n: Int) {}
    val call: AnnotatedCall get() = mutableCall
    internal lateinit var mutableCall: AnnotatedCall

    val name: String by lazy {
        val annotation = this::class.findAnnotation<AnnotationFunctionality>() ?:
            throw IllegalStateException("functionality class not annotated with " +
                    "\"${AnnotationFunctionality::class.qualifiedName}\"")
        annotation.name
    }

    protected inline val pf get() = PredicateFactory
    protected inline val tf get() = TermFactory

    fun single(predicate: Predicate): PredicateState = BasicState(listOf(predicate))
    inline fun single(lambda: () -> Predicate): PredicateState = single(lambda())

    inline fun assume(lambda: PredicateExpression.() -> Predicate): PredicateState =
            single(PredicateExpression.Assume.lambda())
    inline fun state(lambda: PredicateExpression.() -> Predicate): PredicateState =
            single(PredicateExpression.State.lambda())
    inline fun path(lambda: PredicateExpression.() -> Predicate): PredicateState =
            single(PredicateExpression.Path.lambda())

    inline fun term(lambda: TermExpression.() -> Term) = TermExpression.lambda()

    inline fun basic(lambda: ExpressionBuilder.() -> Unit) =
            BasicState(ExpressionBuilder.build(lambda))
    fun chain(first: PredicateState, second: PredicateState) = ChainState(first, second)
    fun choice(states: List<PredicateState>) = ChoiceState(states)
    fun choice(vararg states: PredicateState) = ChoiceState(states.toList())

    override fun toString() = name
}
