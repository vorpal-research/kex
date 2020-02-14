package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.Reanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kex.util.mergeTypes
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.TypeFactory
import java.lang.reflect.Executable
import java.lang.reflect.Type

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

    var typeInfos: TypeInfoMap

    val reanimator: Reanimator<T>

    val memory: MutableMap<Term, T>
    var thisTerm: Term?
    val argTerms: MutableMap<Int, Term>

    val javaClass: Class<*>
    val javaMethod: Executable

    val instance get() = thisTerm?.let { memory[it] }
    val args get() = argTerms.map { memory[it.value] }.toList()

    fun generateThis() = thisTerm?.let {
        memory[it] = reanimator.reanimate(it, javaClass)
    }

    fun generateArgs() =
            argTerms.values.zip(javaMethod.genericParameterTypes).forEach { (term, type) ->
                // TODO: need to think about more clever type info merging
                val castedType = when (val info = typeInfos.getInfo<CastTypeInfo>(term)) {
                    null -> null
                    else -> reanimator.loader.loadClass(info.type.getKfgType(method.cm.type))
                }
                val actualType = when (castedType) {
                    null -> type
                    else -> mergeTypes(castedType, type, reanimator.loader)
                }
                reanimateTerm(term, actualType)
            }

    fun reanimateTerm(term: Term,
                                jType: Type = loader.loadClass(term.type.getKfgType(method.cm.type))): T? = memory.getOrPut(term) {
        when (typeInfos.getInfo<NullabilityInfo>(term)?.nullability) {
            Nullability.NON_NULLABLE -> reanimator.reanimate(term, jType)
            else -> reanimator.reanimateNullable(term, jType)
        }
    }

    fun generate(ps: PredicateState): Pair<T?, List<T?>> {
        val (tempThis, tempArgs) = collectArguments(ps)
        typeInfos = collectTypeInfos(reanimator.model, type, ps)
        if (typeInfos.isNotEmpty())
            log.debug("Collected type info:\n${typeInfos.toList().joinToString("\n")}")
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
        vars.forEach { reanimateTerm(it) }
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