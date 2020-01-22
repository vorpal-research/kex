package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.generator.ConstantDescriptor
import org.jetbrains.research.kex.generator.Descriptor
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.*
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.TypeFactory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

// remove all choices in a given PS
// needed to get entry condition of a given PS
private object ChoiceSimplifier : Transformer<ChoiceSimplifier> {
    override fun transformChoice(ps: ChoiceState): PredicateState {
        return emptyState()
    }
}

private fun mergeTypes(lhv: Type, rhv: Type, loader: ClassLoader): Type {
    @Suppress("NAME_SHADOWING")
    val lhv = lhv as? Class<*> ?: unreachable { log.error("Don't consider merging other types yet") }
    return when (rhv) {
        is Class<*> -> when {
            lhv.isAssignableFrom(rhv) -> rhv
            rhv.isAssignableFrom(lhv) -> lhv
            else -> findSubtypesOf(loader, lhv, rhv).firstOrNull()
                    ?: unreachable { log.error("Cannot decide on argument type: $rhv or $lhv") }
        }
        is ParameterizedType -> {
            val rawType = rhv.rawType as Class<*>
            // todo: find a way to create a new parameterized type with new raw type
            @Suppress("UNUSED_VARIABLE") val actualType = mergeTypes(lhv, rawType, loader) as Class<*>
            rhv
        }
        is TypeVariable<*> -> {
            val bounds = rhv.bounds
            when {
                bounds == null -> lhv
                bounds.isEmpty() -> lhv
                else -> {
                    require(bounds.size == 1)
                    mergeTypes(lhv, bounds.first(), loader)
                }
            }
        }
        else -> {
            log.warn("Merging unexpected types $lhv and $rhv")
            rhv
        }
    }
}

class ModelExecutor(val method: Method,
                    val ctx: ExecutionContext,
                    model: SMTModel) : Transformer<ModelExecutor> {
    private val type: TypeFactory get() = ctx.types
    private val loader: ClassLoader get() = ctx.loader

    private val reanimator: Reanimator<Any?> = ObjectReanimator(method, model, ctx)
    private val descriptors: Reanimator<Descriptor> = DescriptorReanimator(method, model, ctx)
    private val memory = hashMapOf<Term, Any?>()
    private var thisTerm: Term? = null
    private val argTerms = sortedMapOf<Int, Term>()

    private val javaClass = loader.loadClass(type.getRefType(method.`class`))
    private val javaMethod = javaClass.getMethod(method, loader)

    val instance get() = thisTerm?.let { memory[it] }
    val args get() = argTerms.map { memory[it.value] }.toList()

    override fun apply(ps: PredicateState): PredicateState {
        val (tempThis, tempArgs) = collectArguments(ps)
        val argTypeInfo = collectTypeInfos(reanimator.model, ps)
        if (argTypeInfo.isNotEmpty())
            log.debug("Collected type info:\n${argTypeInfo.toList().joinToString("\n")}")
        thisTerm = when {
            !method.isStatic && tempThis == null -> term { `this`(KexClass(method.`class`.fullname)) }
            else -> tempThis
        }
        argTerms.putAll(tempArgs)
        for ((index, type) in method.argTypes.withIndex()) {
            argTerms.getOrPut(index) { term { arg(type.kexType, index) } }
        }
        thisTerm?.let {
            memory[it] = reanimator.reanimateNullable(it, javaClass)
            val descriptor = descriptors.reanimate(it, javaClass)
            log.debug("Descriptor for $it: $descriptor")
            if (descriptor !is ConstantDescriptor)
                log.debug("State for $it: ${descriptor.toState()}")
        }
        argTerms.values.zip(javaMethod.genericParameterTypes).forEach { (term, type) ->
            // TODO: need to think about more clever type info merging
            val castedType = when (term) {
                in argTypeInfo -> reanimator.loader.loadClass(argTypeInfo.getValue(term).getKfgType(method.cm.type))
                else -> null
            }
            val actualType = when (castedType) {
                null -> type
                else -> mergeTypes(castedType, type, reanimator.loader)
            }
            memory[term] = reanimator.reanimateNullable(term, actualType)
            val descriptor = descriptors.reanimateNullable(term, javaClass)
            log.debug("Descriptor for $term: $descriptor")
            if (descriptor !is ConstantDescriptor)
                log.debug("State for $term: ${descriptor.toState()}")
        }
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