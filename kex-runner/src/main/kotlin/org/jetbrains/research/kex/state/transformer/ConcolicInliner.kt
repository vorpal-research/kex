package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexRtManager.isKexRt
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.collection.dequeOf

class ConcolicInliner(
    val ctx: ExecutionContext,
    val typeInfoMap: TypeInfoMap,
    override val psa: PredicateStateAnalysis,
    override val inlineSuffix: String = "inlined",
    override var inlineIndex: Int = 0
) : Inliner<ConcolicInliner> {
    private val knownTypes = hashMapOf<Term, KexType>()
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false

    override fun transformTerm(term: Term): Term {
        if (term.type.isKexRt) knownTypes[term] = term.type
        val typeInfo = typeInfoMap.getInfo<CastTypeInfo>(term) ?: return term
        knownTypes[term] = typeInfo.type
        return term
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val calledMethod = call.method
        if (!isInlinable(calledMethod)) return predicate

        val inlinedMethod = getInlinedMethod(call) ?: return predicate
        var (casts, mappings) = buildMappings(call, inlinedMethod, predicate.lhvUnsafe)

        val callerClass = when (val kexType = call.owner.type) {
            is KexClass -> kexType.kfgClass(ctx.types)
            else -> return predicate //unreachable { log.error("Unknown call owner $kexType") }
        }
        var castPredicate: Predicate? = null
        if (inlinedMethod.klass != callerClass) {
            castPredicate = state {
                val castType = inlinedMethod.klass.kexType
                val casted = value(castType, "${call.owner}.casted${inlineIndex++}")
                mappings = mappings.mapValues { if (it.value == call.owner) casted else it.value }
                casted equality (call.owner `as` castType)
            }
        }
        val inlinedState = prepareInlinedState(inlinedMethod, mappings) ?: return predicate
        castPredicate?.run {
            currentBuilder += this
        }
        casts.onEach { currentBuilder += it }
        currentBuilder += inlinedState
        hasInlined = true
        return nothing()
    }

    override fun isInlinable(method: Method): Boolean = im.inliningEnabled && !im.isIgnored(method)

    override fun getInlinedMethod(callTerm: CallTerm): Method? {
        val method = callTerm.method
        return when {
            method.isFinal -> method
            method.isStatic -> method
            method.isConstructor -> method
            else -> {
                val kexClass = knownTypes[callTerm.owner] as? KexClass ?: return null
                val concreteClass = kexClass.kfgClass(ctx.types) as? ConcreteClass ?: return null
                val result = try {
                    concreteClass.getMethod(method.name, method.desc)
                } catch (e: Exception) {
                    return null
                }
                when {
                    result.isEmpty() -> null
                    else -> result
                }
            }
        }
    }

}