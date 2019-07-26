package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.CallTerm

object IntrinsicAdapter : Transformer<IntrinsicAdapter> {
    private val im = MethodManager.IntrinsicManager

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        return when (val method = call.method) {
            im.getCheckNotNull(method.cm) -> pf.getInequality(call.arguments[0], tf.getNull(), PredicateType.Assume())
            else -> predicate
        }
    }
}