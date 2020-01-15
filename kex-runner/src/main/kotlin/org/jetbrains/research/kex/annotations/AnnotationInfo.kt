package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.term.Term
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

    override fun toString() = name
}
