package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.getExpandedBitsize

class TypeInfoAdapter(val method: Method) : Transformer<TypeInfoAdapter> {

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
}