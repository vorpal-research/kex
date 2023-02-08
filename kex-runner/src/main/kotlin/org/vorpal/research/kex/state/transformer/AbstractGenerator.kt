package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.smt.ModelReanimator
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.state.BasicState
import org.vorpal.research.kex.state.ChoiceState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.emptyState
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

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
    val args get() = argTerms.map { memory[it.value] }
    val staticFields get() = staticFieldOwners.mapTo(mutableSetOf()) { memory[it]!! }

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
            !method.isStatic && tempThis == null -> term {
                `this`(KexClass(method.klass.fullName).rtMapped)
            }

            else -> tempThis
        }
        argTerms.putAll(tempArgs)
        for ((index, type) in method.argTypes.withIndex()) {
            argTerms.getOrPut(index) {
                term {
                    arg(type.kexType.rtMapped, index)
                }
            }
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
        val vars = collectPointers(ps, ignoreLambdaParams = true)
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
        val ourChoice = paths.firstOrNull { path ->
            path.second.all { checkPath(it) }
        }?.first ?: return emptyState()
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
