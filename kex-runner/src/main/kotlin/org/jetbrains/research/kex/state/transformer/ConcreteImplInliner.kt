package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.collection.dequeOf
import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.TypeFactory

class ConcreteImplInliner(val types: TypeFactory,
                          val typeInfoMap: TypeInfoMap,
                          override val psa: PredicateStateAnalysis,
                          override var inlineIndex: Int = 0) : Inliner<ConcreteImplInliner> {
    override val im = MethodManager.InlineManager
    override val builders = dequeOf(StateBuilder())

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
}