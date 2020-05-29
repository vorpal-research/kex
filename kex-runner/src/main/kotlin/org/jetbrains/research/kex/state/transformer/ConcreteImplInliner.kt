package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.assert.ktassert
import com.abdullin.kthelper.collection.dequeOf
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method

private val defaultDepth = kexConfig.getIntValue("inliner", "depth", 10)

class ConcreteImplInliner(val ctx: ExecutionContext,
                          val typeInfoMap: TypeInfoMap,
                          val psa: PredicateStateAnalysis,
                          inlineIndex: Int = 0) : RecollectingTransformer<ConcreteImplInliner> {
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

    private fun getConcreteImpl(call: CallTerm): Method? {
        val method = call.method
        return when {
            method.isFinal -> method
            method.isStatic -> method
            method.isConstructor -> method
            else -> {
                val typeInfo = typeInfoMap.getInfo<CastTypeInfo>(call.owner) ?: return null
                val kexClass = typeInfo.type as? KexClass ?: return null
                val concreteClass = kexClass.getKfgClass(ctx.types) as? ConcreteClass ?: return null
                val result = concreteClass.getMethod(method.name, method.desc)
                ktassert(result.isNotEmpty())
                result
            }
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        if (!im.inliningEnabled || im.isIgnored(call.method)) return predicate
        val calledMethod = getConcreteImpl(call) ?: return predicate

        val mappings = hashMapOf<Term, Term>()
        if (!call.isStatic) {
            val `this` = term { `this`(call.owner.type) }
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

class DepthInliner(val ctx: ExecutionContext,
                   val typeInfoMap: TypeInfoMap,
                   val psa: PredicateStateAnalysis,
                   val maxDepth: Int = defaultDepth) : Transformer<DepthInliner> {
    override fun apply(ps: PredicateState): PredicateState {
        var last: PredicateState
        var current = ps
        var inlineIndex = 0
        var depth = 0
        do {
            last = current
            val cii = ConcreteImplInliner(ctx, typeInfoMap, psa, inlineIndex)
            current = cii.apply(last)
            inlineIndex = cii.inlineIndex
            ++depth
        } while (current != last && depth < maxDepth)
        return current
    }
}