package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.collection.dequeOf

class ConcreteImplInliner(val types: TypeFactory,
                          val typeInfoMap: TypeInfoMap,
                          override val psa: PredicateStateAnalysis,
                          override val inlineSuffix: String = "concrete.inlined",
                          override var inlineIndex: Int = 0) : Inliner<ConcreteImplInliner> {
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false

    override fun isInlinable(method: Method): Boolean = im.inliningEnabled && !im.isIgnored(method)

    override fun getInlinedMethod(callTerm: CallTerm): Method? {
        val method = callTerm.method
        return when {
            method.isFinal -> method
            method.isStatic -> method
            method.isConstructor -> method
            else -> {
                val typeInfo = typeInfoMap.getInfo<CastTypeInfo>(callTerm.owner) ?: return null
                val kexClass = typeInfo.type as? KexClass ?: return null
                val concreteClass = kexClass.kfgClass(types) as? ConcreteClass ?: return null
                val result = try {
                    concreteClass.getMethod(method.name, method.desc)
                } catch (e: Exception) {
                    return null
                }
                when {
                    result.body.isEmpty() -> null
                    else -> result
                }
            }
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val calledMethod = call.method
        if (!isInlinable(calledMethod)) return predicate

        val inlinedMethod = getInlinedMethod(call) ?: return predicate
        var (casts, mappings) = buildMappings(call, inlinedMethod, predicate.lhvUnsafe)

        val callerClass = when (val kexType = call.owner.type) {
            is KexClass ->  kexType.kfgClass(types)
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
}

class AliasingConcreteImplInliner(val types: TypeFactory,
                                  val typeInfoMap: TypeInfoMap,
                                  val aa: AliasAnalysis,
                                  override val psa: PredicateStateAnalysis,
                                  override val inlineSuffix: String = "aliasing.inlined",
                                  override var inlineIndex: Int = 0) : Inliner<ConcreteImplInliner> {
    override val im = MethodManager.InlineManager
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false

    override fun isInlinable(method: Method): Boolean = im.inliningEnabled && !im.isIgnored(method)

    override fun getInlinedMethod(callTerm: CallTerm): Method? {
        val method = callTerm.method
        return when {
            method.isFinal -> method
            method.isStatic -> method
            method.isConstructor -> method
            method.isKexRt -> method
            else -> {
                val typeInfo = run {
                    when (val owner = callTerm.owner) {
                        in typeInfoMap -> typeInfoMap.getInfo<CastTypeInfo>(owner)
                        else -> {
                            val aliases = typeInfoMap.keys.filter { aa.mayAlias(it, owner) }
                            if (aliases.isNotEmpty()) typeInfoMap.getInfo<CastTypeInfo>(aliases.first())
                            else null
                        }
                    }
                } ?: return null
                val kexClass = typeInfo.type as? KexClass ?: return null
                val concreteClass = kexClass.kfgClass(types) as? ConcreteClass ?: return null
                val result = try {
                    concreteClass.getMethod(method.name, method.desc)
                } catch (e: Exception) {
                    return null
                }
                when {
                    result.body.isEmpty() -> null
                    else -> result
                }
            }
        }
    }
}