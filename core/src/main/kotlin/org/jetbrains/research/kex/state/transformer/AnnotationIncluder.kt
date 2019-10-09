package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.annotations.AnnotationsLoader
import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
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
        val returnValue = predicate.lhvUnsafe
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
            val ps = expand(state)
            @Suppress("UNCHECKED_CAST")
            when (ps) {
                is List<*> -> predicates += ps as List<Predicate>
                is PredicateState -> {
                    currentBuilder += BasicState(predicates.toList())
                    currentBuilder += ps
                    predicates.clear()
                }
            }
        }
        if (predicates.isNotEmpty())
            currentBuilder += BasicState(predicates.toList())
        return nothing()
    }

    private fun expand(ps: PredicateState): Any = when (ps) {
        is ChainState -> expandChain(ps)
        is BasicState -> ps.predicates
        is ChoiceState -> ps
        else -> unreachable {  }
    }

    @Suppress("UNCHECKED_CAST")
    private fun expandChain(ps: ChainState): Any {
        val base = expand(ps.base)
        val curr = expand(ps.curr)
        if (base is List<*> && curr is List<*>)
            return base + curr
        if (base is List<*> && curr is PredicateState)
            return ChainState(BasicState(base as List<Predicate>), curr)
        if (base is PredicateState && curr is List<*>)
            return ChainState(base, BasicState(curr as List<Predicate>))
        if (base is PredicateState && curr is PredicateState)
            return ChainState(base, curr)
        return unreachable { }
    }
}