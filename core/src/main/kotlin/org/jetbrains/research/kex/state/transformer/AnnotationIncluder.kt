package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.annotations.AnnotationsLoader
import org.jetbrains.research.kex.state.BasicState
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
        //log.debug { "try to find ${method.prototype}" }
        val annotatedCall = annotations.getExactCall("${method.`class`}.${method.name}",
                *Array(argTypes.size) { argTypes[it].name }) ?: return predicate
        log.debug { "found annotations for $annotatedCall ${annotatedCall.annotations} and for params " +
                "(${annotatedCall.params.joinToString { "${it.type}:${it.annotations}" }})" }
        val predicates = mutableListOf<Predicate>()
        for ((i, param) in annotatedCall.params.withIndex()) {
            for (annotation in param.annotations) {
                val arg = args[i]
                predicates += annotation.valuePrecise(arg)
                predicates += annotation.paramPrecise(args[i], i)
            }
        }
        for (annotation in annotatedCall.annotations)
            predicates += annotation.callPreciseBefore(predicate)
        predicates += predicate
        val returnValue = predicate.getLhvUnsafe()
        for (annotation in annotatedCall.annotations) {
            predicates += annotation.callPreciseAfter(predicate)
            if (returnValue !== null) {
                predicates += annotation.valuePrecise(returnValue)
                predicates += annotation.returnPrecise(returnValue)
            }
        }
        currentBuilder += BasicState(predicates)
        return Transformer.Stub
    }

}