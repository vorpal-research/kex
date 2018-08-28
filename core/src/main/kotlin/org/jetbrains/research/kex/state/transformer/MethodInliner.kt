package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.InlineManager
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.UnreachableInst

class MethodInliner(val method: Method) : DeletingTransformer<MethodInliner> {
    override val removablePredicates = hashSetOf<Predicate>()
    val stateBuilder = StateBuilder()
    var currentBuilder = StateBuilder()
    private var inlineIndex = 0

    override fun apply(ps: PredicateState): PredicateState {
        super.transform(ps)
        stateBuilder += currentBuilder.apply()
        val resultingState = stateBuilder.apply().simplify()
        return resultingState.filter { it !in removablePredicates }
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        stateBuilder += currentBuilder.apply()
        val newChoices = arrayListOf<PredicateState>()
        for (choice in ps.choices) {
            currentBuilder = StateBuilder()
            super.transformBase(choice)

            newChoices.add(currentBuilder.apply())
        }
        currentBuilder = StateBuilder()
        stateBuilder += ChoiceState(newChoices)
        return ps
    }

    override fun transformPredicate(predicate: Predicate): Predicate {
        currentBuilder = currentBuilder + predicate
        return predicate
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val calledMethod = call.method
        if (InlineManager.isInlinable(method)) return predicate

        val mappings = hashMapOf<Term, Term>()
        if (!call.isStatic) {
            val `this` = tf.getThis(call.owner.type)
            mappings[`this`] = call.owner
        }
        if (predicate.hasLhv) {
            val retval = tf.getReturn(call.method)
            mappings[retval] = predicate.lhv
        }

        for ((index, argType) in calledMethod.desc.args.withIndex()) {
            val argTerm = tf.getArgument(argType.kexType, index)
            val calledArg = call.arguments[index]
            mappings[argTerm] = calledArg
        }

        currentBuilder = currentBuilder + prepareInlinedState(calledMethod, mappings)

        removablePredicates.add(predicate)
        return predicate
    }

    private fun prepareInlinedState(method: Method, mappings: Map<Term, Term>): PredicateState {
        val builder = PredicateStateAnalysis.builder(method)
        val lastInst = method.flatten().last { it !is UnreachableInst }
        val endState = builder.getInstructionState(lastInst)
                ?: unreachable { log.error("Can't get state for inlined method $method\n${method.print()}") }

        val remapped = TermRemapper("inlined${inlineIndex++}", mappings).apply(endState)
        return remapped
    }
}

private class TermRemapper(val suffix: String, val remapping: Map<Term, Term>) : Transformer<TermRemapper> {
    override fun transformTerm(term: Term): Term = remapping[term] ?: when (term) {
        is ValueTerm, is ArgumentTerm, is ReturnValueTerm -> tf.getValue(term.type, "${term.name}.$suffix")
        else -> term
    }
}