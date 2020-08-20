package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.collection.dequeOf
import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.Method

private val defaultDepth = kexConfig.getIntValue("inliner", "depth", 5)

class MethodInliner(val psa: PredicateStateAnalysis,
                    inlineIndex: Int = 0) : RecollectingTransformer<MethodInliner> {
    private val im = MethodManager.InlineManager
    override val builders = dequeOf(StateBuilder())
    var inlineIndex = inlineIndex
        private set

    protected class TermRenamer(val suffix: String, val remapping: Map<Term, Term>) : Transformer<TermRenamer> {
        override fun transformTerm(term: Term): Term = remapping[term] ?: when (term) {
            is ValueTerm, is ArgumentTerm, is ReturnValueTerm -> term { value(term.type, "${term.name}.$suffix") }
            else -> term
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val calledMethod = call.method
        if (!im.isInlinable(calledMethod)) return predicate

        val mappings = hashMapOf<Term, Term>()
        if (!call.isStatic) {
            val `this` = term { `this`(calledMethod.`class`.kexType) }
            mappings[`this`] = call.owner
        }
        if (predicate.hasLhv) {
            val retval = term { `return`(calledMethod) }
            mappings[retval] = predicate.lhv
        }

        for ((index, argType) in calledMethod.argTypes.withIndex()) {
            val argTerm = term { arg(argType.kexType, index) }
            val calledArg = call.arguments[index]
            mappings[argTerm] = calledArg
        }

        currentBuilder += prepareInlinedState(calledMethod, mappings) ?: return predicate

        return nothing()
    }

    private fun prepareInlinedState(method: Method, mappings: Map<Term, Term>): PredicateState? {
        if (method.isEmpty()) return null

        val builder = psa.builder(method)
        val endState = builder.methodState ?: return null

        return TermRenamer("inlined${inlineIndex++}", mappings).apply(endState)
    }
}

class SimpleDepthInliner(val psa: PredicateStateAnalysis, val maxDepth: Int = defaultDepth) : Transformer<DepthInliner> {
    override fun apply(ps: PredicateState): PredicateState {
        var last: PredicateState
        var current = ps
        var inlineIndex = 0
        var depth = 0
        do {
            last = current
            val cii = MethodInliner(psa, inlineIndex)
            current = cii.apply(last)
            inlineIndex = cii.inlineIndex
            ++depth
        } while (current != last && depth < maxDepth)
        return current
    }
}