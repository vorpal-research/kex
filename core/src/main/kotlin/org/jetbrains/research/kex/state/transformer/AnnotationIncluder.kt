package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.annotations.AnnotationsLoader
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import java.util.*

class AnnotationIncluder(val annotations: AnnotationsLoader) : RecollectingTransformer<AnnotationIncluder> {

    override val builders = ArrayDeque<StateBuilder>().apply { push(StateBuilder()) }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val method = call.method
        val args = call.arguments
        val argTypes = method.argTypes
        val annotatedCall = annotations.getExactCall("${method.`class`}.${method.name}",
                *Array(argTypes.size) { argTypes[it].name }) ?: return predicate
        log.debug { "found annotations for $annotatedCall ${annotatedCall.annotations} and for params " +
                "(${annotatedCall.params.joinToString { "${it.type}:${it.annotations}" }})" }
        val states = mutableListOf<PredicateState>()
        for ((i, param) in annotatedCall.params.withIndex()) {
            for (annotation in param.annotations) {
                val arg = args[i]
                annotation.preciseValue(arg)?.run { states += this }
                states += annotation.preciseParam(args[i], i) ?: continue
            }
        }
        for (annotation in annotatedCall.annotations)
            states += annotation.preciseBeforeCall(predicate) ?: continue
        states += BasicState(Collections.singletonList(predicate))
        val returnValue = predicate.getLhvUnsafe()
        for (annotation in annotatedCall.annotations) {
            annotation.preciseAfterCall(predicate)?.run { states += this }
            if (returnValue !== null) {
                annotation.preciseValue(returnValue)?.run { states += this }
                states += annotation.preciseReturn(returnValue) ?: continue
            }
        }
        // Concatenate some States for better presentation
        val predicates = mutableListOf<Predicate>()
        for (state in states) {
            when (state) {
                is BasicState -> predicates += state.predicates
                else -> {
                    if (state.isNotEmpty) {
                        currentBuilder += BasicState(predicates)
                        predicates.clear()
                        currentBuilder += state
                    }
                }
            }
        }
        if (predicates.isNotEmpty())
            currentBuilder += BasicState(predicates)
        return Transformer.Stub
    }

}