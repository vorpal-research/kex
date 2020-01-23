package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.Reanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.TypeFactory

abstract class AbstractGenerator<T>(val method: Method, val ctx: ExecutionContext, val model: SMTModel) {
    protected val type: TypeFactory get() = ctx.types
    protected val loader: ClassLoader get() = ctx.loader
    protected val memory = hashMapOf<Term, T>()

    protected var thisTerm: Term? = null
    protected val argTerms = sortedMapOf<Int, Term>()

    private val javaClass = loader.loadClass(type.getRefType(method.`class`))
    private val javaMethod = when {
        method.isConstructor -> javaClass.getConstructor(method, loader)
        else -> javaClass.getMethod(method, loader)
    }

    abstract val reanimator: Reanimator<T>

    val instance get() = thisTerm?.let { memory[it] }
    val args get() = argTerms.map { memory[it.value] }.toList()

    protected open fun generateThis() = thisTerm?.let {
        memory[it] = reanimator.reanimate(it, javaClass)
    }

    protected open fun generateArgs(types: TypeInfoMap) =
            argTerms.values.zip(javaMethod.genericParameterTypes).forEach { (term, type) ->
                // TODO: need to think about more clever type info merging
                val castedType = when (val info = types.getInfo<CastTypeInfo>(term)) {
                    null -> null
                    else -> reanimator.loader.loadClass(info.type.getKfgType(method.cm.type))
                }
                val actualType = when (castedType) {
                    null -> type
                    else -> mergeTypes(castedType, type, reanimator.loader)
                }
                memory[term] = when (types.getInfo<NullabilityInfo>(term)?.nullability) {
                    Nullability.NON_NULLABLE -> reanimator.reanimate(term, actualType)
                    else -> reanimator.reanimateNullable(term, actualType)
                }
            }

    fun generate(ps: PredicateState): Pair<T?, List<T?>> {
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
        generateThis()
        generateArgs(argTypeInfo)
        return instance to args
    }
}