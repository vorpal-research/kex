package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.type.getExpandedBitsize

class TypeInfoAdapter(val method: Method) : Transformer<TypeInfoAdapter> {
    companion object {
        val intrinsics = CM.getByName("kotlin/jvm/internal/Intrinsics")
        val checkNotNull = intrinsics.getMethod(
                "checkParameterIsNotNull",
                MethodDesc(
                        arrayOf(TF.objectType, TF.stringType),
                        TF.voidType
                )
        )
    }

    fun doit(ps: PredicateState): PredicateState {
        val builder = StateBuilder()
        if (!method.isAbstract) {
            val `this` = tf.getThis(method.`class`)
            val `null` = tf.getNull()
            builder += pf.getInequality(`this`, `null`, PredicateType.Assume())

            val typeSize = `this`.type.getExpandedBitsize()
            val value = tf.getInt(typeSize)
            builder += pf.getBoundStore(`this`, value, PredicateType.Assume())
        }

        val newState = (builder + ps).apply()
        return transform(newState)
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        if (call.method == checkNotNull) {

            val ptr = call.arguments[0]

            val newPred = pf.getInequality(ptr, tf.getNull())
            return newPred
        }
        return predicate
    }
}