package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.ModelReanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.TypeFactory

// remove all choices in a given PS
// needed to get entry condition of a given PS
private object ChoiceSimplifier : Transformer<ChoiceSimplifier> {
    override fun transformChoice(ps: ChoiceState): PredicateState {
        return emptyState()
    }
}

interface AbstractGenerator<T> : Transformer<AbstractGenerator<T>> {
    val method: Method
    val ctx: ExecutionContext
    val model: SMTModel

    val type: TypeFactory get() = ctx.types
    val loader: ClassLoader get() = ctx.loader

    val modelReanimator: ModelReanimator<T>

    val memory: MutableMap<Term, T>
    var thisTerm: Term?
    val argTerms: MutableMap<Int, Term>
    val staticFieldOwners: MutableSet<Term>

    val instance get() = thisTerm?.let { memory[it] }
    val args get() = argTerms.map { memory[it.value] }.toList()
    val staticFields get() = staticFieldOwners.map { memory[it]!! }.toSet()

    fun generateThis() = thisTerm?.let {
        memory[it] = modelReanimator.reanimate(it)
    }

    fun generateArgs() = argTerms.values.forEach { term ->
        reanimateTerm(term)
    }

    fun reanimateTerm(term: Term): T? = memory.getOrPut(term) {
        modelReanimator.reanimate(term)
    }

    fun generate(ps: PredicateState): Pair<T?, List<T?>> {
        val (tempThis, tempArgs) = collectArguments(ps)
        thisTerm = when {
            !method.isStatic && tempThis == null -> term { `this`(KexClass(method.`class`.fullname)) }
            else -> tempThis
        }
        argTerms.putAll(tempArgs)
        for ((index, type) in method.argTypes.withIndex()) {
            argTerms.getOrPut(index) { term { arg(type.kexType, index) } }
        }
        generateThis()
        generateArgs()
        return instance to args
    }

    override fun apply(ps: PredicateState): PredicateState {
        generate(ps)
        return super.apply(ps)
    }

    override fun transformBasic(ps: BasicState): PredicateState {
        val vars = collectPointers(ps)
        vars.forEach { ptr ->
            if (ptr is FieldTerm && ptr.isStatic) {
                staticFieldOwners += ptr.owner
            }
            reanimateTerm(ptr)
        }
        return ps
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val paths = ps.choices.map { it to it.path }.map {
            it.first to ChoiceSimplifier.apply(it.second)
        }
        val ourChoice = paths.firstOrNull { it.second.all { checkPath(it) } }?.first ?: return emptyState()
        return super.transformBase(ourChoice)
    }

    fun checkTerms(lhv: Term, rhv: Term, cmp: (Any?, Any?) -> Boolean): Boolean {
        val lhvValue = reanimateTerm(lhv)
        val rhvValue = when (rhv) {
            is ConstBoolTerm -> rhv.value
            is ConstIntTerm -> rhv.value
            is ConstLongTerm -> rhv.value
            else -> unreachable { log.error("Unexpected constant in path $rhv") }
        }
        return cmp(lhvValue, rhvValue)
    }

    fun checkPath(path: Predicate): Boolean
}