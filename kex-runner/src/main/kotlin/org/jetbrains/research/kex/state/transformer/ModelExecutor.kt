package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.generator.AbstractGenerator
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.ObjectReanimator
import org.jetbrains.research.kex.smt.ReanimatedModel
import org.jetbrains.research.kex.smt.Reanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.ConstLongTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method

// remove all choices in a given PS
// needed to get entry condition of a given PS
private object ChoiceSimplifier : Transformer<ChoiceSimplifier> {
    override fun transformChoice(ps: ChoiceState): PredicateState {
        return emptyState()
    }
}

class ModelExecutor(method: Method, ctx: ExecutionContext, model: SMTModel) :
        AbstractGenerator<Any?>(method, ctx, model), Transformer<ModelExecutor> {
    override val reanimator: Reanimator<Any?> = ObjectReanimator(method, model, ctx)

    override fun generateThis() = thisTerm?.let {
        memory[it] = reanimator.reanimateNullable(it, javaClass)
    }

    override fun apply(ps: PredicateState): PredicateState {
        generate(ps)
        return super.apply(ps)
    }

    override fun transformBasic(ps: BasicState): PredicateState {
        val vars = collectPointers(ps)
        vars.forEach { ptr -> memory.getOrPut(ptr) { reanimator.reanimateNullable(ptr) } }
        return ps
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val paths = ps.choices.map { it to it.path }.map {
            it.first to ChoiceSimplifier.apply(it.second)
        }
        val ourChoice = paths.firstOrNull { it.second.all { checkPath(it) } }?.first ?: return emptyState()
        return super.transformBase(ourChoice)
    }

    private fun checkTerms(lhv: Term, rhv: Term, cmp: (Any?, Any?) -> Boolean): Boolean {
        val lhvValue = memory.getOrPut(lhv) { reanimator.reanimateNullable(lhv) }
        val rhvValue = when (rhv) {
            is ConstBoolTerm -> rhv.value
            is ConstIntTerm -> rhv.value
            is ConstLongTerm -> rhv.value
            else -> unreachable { log.error("Unexpected constant in path $rhv") }
        }
        return cmp(lhvValue, rhvValue)
    }

    private fun checkPath(path: Predicate): Boolean = when (path) {
        is EqualityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a == b }
        is InequalityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a != b }
        is DefaultSwitchPredicate -> {
            val lhv = path.cond
            val conditions = path.cases
            val lhvValue = memory.getOrPut(lhv) { reanimator.reanimateNullable(lhv) }
            val condValues = conditions.map { (it as ConstIntTerm).value }
            lhvValue !in condValues
        }
        else -> unreachable { log.error("Unexpected predicate in path: $path") }
    }
}

fun executeModel(ctx: ExecutionContext,
                 ps: PredicateState,
                 method: Method,
                 model: SMTModel): ReanimatedModel {
    val pathExecutor = ModelExecutor(method, ctx, model)
    pathExecutor.apply(ps)
    return ReanimatedModel(method, pathExecutor.instance, pathExecutor.args)
}

fun generateInputByModel(ctx: ExecutionContext,
                         method: Method,
                         ps: PredicateState,
                         model: SMTModel): Pair<Any?, Array<Any?>> {
    val reanimated = executeModel(ctx, ps, method, model)
    val loader = ctx.loader

    val instance = reanimated.instance ?: when {
        method.isStatic -> null
        else -> tryOrNull {
            val klass = loader.loadClass(ctx.types.getRefType(method.`class`))
            ctx.random.next(klass)
        }
    }

    if (instance == null && !method.isStatic) {
        throw GenerationException("Unable to create or generate instance of class ${method.`class`}")
    }
    return instance to reanimated.arguments.toTypedArray()
}