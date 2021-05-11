package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kthelper.collection.dequeOf
import java.util.*

object IntrinsicAdapter : Transformer<IntrinsicAdapter> {
//    override val builders: Deque<StateBuilder> = dequeOf(StateBuilder())
    private val im = MethodManager.IntrinsicManager
    private val kim = MethodManager.KexIntrinsicManager

    private fun getAllAssertions(method: Method, assertionsArray: Term): Set<Term> = method.flatten()
        .asSequence()
        .mapNotNull { it as? ArrayStoreInst }
        .filter { it.arrayRef.toString() == assertionsArray.toString() }
        .map { it.value }
        .map { term { value(it) } }
        .toSet()

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        return when (val method = call.method) {
            im.checkParameterIsNotNull(method.cm) -> assume { call.arguments[0] inequality null }
            im.checkNotNullParameter(method.cm) -> assume { call.arguments[0] inequality null }
            im.areEqual(method.cm) -> state { predicate.lhv equality (call.arguments[0] eq call.arguments[1]) }
//            kim.kexAssume(method.cm) -> {
//                for (assert in getAllAssertions(method, call.arguments[0])) {
//                    currentBuilder += assume { assert equality true }
//                }
//                nothing()
//            }
            else -> predicate
        }
    }
}