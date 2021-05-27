package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.ktype.KexBool
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

class KexIntrinsicsAdapter : RecollectingTransformer<KexIntrinsicsAdapter> {
    override val builders = dequeOf(StateBuilder())
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
        val predicates = mutableListOf<Predicate>()
        when (val method = call.method) {
            kim.kexAssume(method.cm) -> {
                val assertions = getAllAssertions(method, call.arguments[0])
                for (assertion in assertions) {
                    predicates += assume { assertion equality true }
                }
            }
            kim.kexNotNull(method.cm) -> {
                val value = call.arguments[0]
                val term = term { generate(KexBool()) }
                predicates += state { term equality (value eq null) }
                predicates += assume { term equality false }
            }
        }
        for (assertion in predicates) {
            currentBuilder += assertion
        }
        return when {
            predicates.isEmpty() -> predicate
            else -> nothing()
        }
    }
}