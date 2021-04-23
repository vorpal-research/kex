package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.TypeFactory

class ConcreteImplInliner(val types: TypeFactory,
                          val typeInfoMap: TypeInfoMap,
                          override val psa: PredicateStateAnalysis,
                          override val inlineSuffix: String = "concrete.inlined",
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
                    result.isEmpty() -> null
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
        if (inlinedMethod.`class` != callerClass) {
            castPredicate = state {
                val castType = inlinedMethod.`class`.kexType
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
                    result.isEmpty() -> null
                    else -> result
                }
            }
        }
    }
}