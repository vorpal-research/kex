package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import java.util.*
import kotlin.reflect.full.findAnnotation

abstract class AnnotationInfo {

    open fun paramPrecise(value: Term, n: Int): List<Predicate> = emptyList()
    open fun valuePrecise(value: Term): List<Predicate> = emptyList()
    open fun callPreciseBefore(predicate: CallPredicate): List<Predicate> = emptyList()
    open fun callPreciseAfter(predicate: CallPredicate): List<Predicate> = emptyList()
    open fun returnPrecise(value: Term): List<Predicate> = emptyList()

    open fun initialize(n: Int) {}
    val call: AnnotatedCall get() = mutableCall
    internal lateinit var mutableCall: AnnotatedCall

    val name: String by lazy {
        val annotation = this::class.findAnnotation<AnnotationFunctionality>() ?:
            throw IllegalStateException("functionality class not annotated with " +
                    "\"${AnnotationFunctionality::class.qualifiedName}\"")
        annotation.name
    }

    protected val pf get() = PredicateFactory
    protected val tf get() = TermFactory

    companion object {
        fun single(predicate: Predicate): List<Predicate> {
            return Collections.singletonList(predicate)
        }
        inline fun single(lambda: () -> Predicate): List<Predicate> {
            return single(lambda())
        }

        inline fun assume(lambda: PredicateExpression.() -> Predicate): List<Predicate> {
            return single(PredicateExpression.Assume.lambda())
        }
        inline fun state(lambda: PredicateExpression.() -> Predicate): List<Predicate> {
            return single(PredicateExpression.State.lambda())
        }
        inline fun path(lambda: PredicateExpression.() -> Predicate): List<Predicate> {
            return single(PredicateExpression.Path.lambda())
        }
        inline fun build(lambda: ExpressionBuilder.() -> Unit) = ExpressionBuilder.build(lambda)
    }

    override fun toString() = name
}
