package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.model.ObjectRecoverer
import org.jetbrains.research.kex.smt.model.RecoveredModel
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.ConstLongTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method

// remove all choices in a given PS
// needed to get entry condition of a given PS
private object ChoiceSimplifier : Transformer<ChoiceSimplifier> {
    override fun transformChoice(ps: ChoiceState): PredicateState {
        return emptyState()
    }
}

class ModelExecutor(val method: Method, model: SMTModel, loader: ClassLoader) : Transformer<ModelExecutor> {
    private val recoverer = ObjectRecoverer(method, model, loader)
    private val memory = hashMapOf<Term, Any?>()
    private var thisTerm: Term? = null
    private val argTerms = mutableMapOf<Int, Term>()

    val instance get() = thisTerm?.let { memory[it] }
    val args get() = argTerms.asSequence().sortedBy { it.key }.map { memory[it.value] }.toList()

    override fun apply(ps: PredicateState): PredicateState {
        val (tempThis, tempArgs) = collectArguments(ps)
        thisTerm = when {
            !method.isStatic && tempThis == null -> tf.getThis(KexClass(method.`class`.fullname))
            else -> tempThis
        }
        argTerms.putAll(tempArgs)
        for ((index, type) in method.argTypes.withIndex()) {
            argTerms.getOrPut(index) { tf.getArgument(type.kexType, index) }
        }
        thisTerm?.let { memory[it] = recoverer.recoverTerm(it) }
        argTerms.values.forEach { memory[it] = recoverer.recoverTerm(it) }
        return super.apply(ps)
    }

    override fun transformBasic(ps: BasicState): PredicateState {
        val vars = collectPointers(ps)
        vars.forEach { ptr -> memory.getOrPut(ptr) { recoverer.recoverTerm(ptr) } }
        return ps
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val paths = ps.choices.map { it to it.filterByType(PredicateType.Path()) }.map {
            it.first to ChoiceSimplifier.apply(it.second)
        }
        val ourChoice = paths.firstOrNull { it.second.all { checkPath(it) } }?.first ?: return emptyState()
        return super.transformBase(ourChoice)
    }

    private fun checkPath(path: Predicate): Boolean = when (path) {
        is EqualityPredicate -> {
            val lhv = path.lhv
            val rhv = path.rhv
            val lhvValue = memory.getOrPut(lhv) { recoverer.recoverTerm(lhv) }
            val rhvValue = when (rhv) {
                is ConstBoolTerm -> rhv.value
                is ConstIntTerm -> rhv.value
                is ConstLongTerm -> rhv.value
                else -> unreachable { log.error("Unexpected constant in path $rhv") }
            }
            lhvValue == rhvValue
        }
        is InequalityPredicate -> {
            val lhv = path.lhv
            val rhv = path.rhv
            val lhvValue = memory.getOrPut(lhv) { recoverer.recoverTerm(lhv) }
            val rhvValue = when (rhv) {
                is ConstBoolTerm -> rhv.value
                is ConstIntTerm -> rhv.value
                is ConstLongTerm -> rhv.value
                else -> unreachable { log.error("Unexpected constant in path $rhv") }
            }
            lhvValue != rhvValue
        }
        is DefaultSwitchPredicate -> {
            val lhv = path.cond
            val conditions = path.cases
            val lhvValue = memory.getOrPut(lhv) { recoverer.recoverTerm(lhv) }
            val condValues = conditions.map { (it as ConstIntTerm).value }
            lhvValue !in condValues
        }
        else -> unreachable { log.error("Unexpected predicate in path: $path") }
    }
}

fun executeModel(ps: PredicateState, method: Method, model: SMTModel, loader: ClassLoader): RecoveredModel {
    val pathExecutor = ModelExecutor(method, model, loader)
    pathExecutor.apply(ps)
    return RecoveredModel(method, pathExecutor.instance, pathExecutor.args)
}