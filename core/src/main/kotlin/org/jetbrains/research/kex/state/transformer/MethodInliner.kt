package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
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
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import java.util.*

class MethodInliner(val method: Method) : DeletingTransformer<MethodInliner> {
    override val removablePredicates = hashSetOf<Predicate>()

    val stateBuilder: StateBuilder
        get() = builders.peek()

    private val builders = Stack<StateBuilder>()
    private var inlineIndex = 0

    init {
        builders.push(StateBuilder())
    }

    override fun apply(ps: PredicateState): PredicateState {
        super.transform(ps)
        val resultingState = stateBuilder.apply().simplify()
        return resultingState.filter { it !in removablePredicates }
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val newChoices = arrayListOf<PredicateState>()
        for (choice in ps.choices) {
            builders.add(StateBuilder())
            super.transformBase(choice)

            newChoices.add(stateBuilder.apply())
            builders.pop()
        }
        stateBuilder += ChoiceState(newChoices)
        return ps
    }

    override fun transformPredicate(predicate: Predicate): Predicate {
        stateBuilder += predicate
        return predicate
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val calledMethod = call.method
        if (!MethodManager.isInlinable(calledMethod)) return predicate

        val mappings = hashMapOf<Term, Term>()
        if (!call.isStatic) {
            val `this` = tf.getThis(call.owner.type)
            mappings[`this`] = call.owner
        }
        if (predicate.hasLhv) {
            val retval = tf.getReturn(calledMethod)
            mappings[retval] = predicate.lhv
        }

        for ((index, argType) in calledMethod.desc.args.withIndex()) {
            val argTerm = tf.getArgument(argType.kexType, index)
            val calledArg = call.arguments[index]
            mappings[argTerm] = calledArg
        }

        stateBuilder += prepareInlinedState(calledMethod, mappings) ?: return predicate

        removablePredicates.add(predicate)
        return predicate
    }

    private fun prepareInlinedState(method: Method, mappings: Map<Term, Term>): PredicateState? {
        val builder = PredicateStateAnalysis.builder(method)
        val returnInst = method.flatten().firstOrNull { it is ReturnInst }
                ?: unreachable { log.error("Cannot inline method with no return") }
        val endState = builder.getInstructionState(returnInst) ?: return null

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