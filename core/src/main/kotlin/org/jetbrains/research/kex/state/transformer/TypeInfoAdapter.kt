package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.smt.z3.expandedBitsize
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc

class TypeInfoAdapter(val method: Method) : Transformer<TypeInfoAdapter> {
    val validTerms = hashSetOf<Term>()

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

    override fun apply(ps: PredicateState): PredicateState {
        val builder = StateBuilder()
        if (!method.isAbstract) {
            validTerms.add(tf.getThis(method.`class`))
        }

        val originalState = super.transform(ps)

        val `null` = tf.getNull()
        for (valid in validTerms) {
            builder += pf.getInequality(valid, `null`, PredicateType.Assume())

            val typeSize = valid.type.expandedBitsize
            val value = tf.getInt(typeSize)
            builder += pf.getBoundStore(valid, value, PredicateType.Assume())
        }

        return (builder + originalState).apply()
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        if (call.method == checkNotNull) {
            val ptr = call.arguments[0]
            validTerms.add(ptr)
        }
        return predicate
    }
}