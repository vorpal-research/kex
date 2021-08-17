package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.basic
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
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

        val method = call.method
        val cm = method.cm
        val ps = when (call.method.klass) {
            kim.assertionsIntrinsics(cm) -> assertionIntrinsicsAdapter(method, call)
            kim.collectionIntrinsics(cm) -> collectionIntrinsicsAdapter(method, call) { predicate.lhv }
            else -> emptyState()
        }
        currentBuilder += ps
        return when {
            ps.isEmpty -> predicate
            else -> nothing()
        }
    }

    private fun assertionIntrinsicsAdapter(method: Method, call: CallTerm): PredicateState = basic {
        when (method) {
            kim.kexAssume(method.cm) -> {
                val assertions = getAllAssertions(method, call.arguments[0])
                assertions.map {
                    assume { it equality true }
                }
            }
            kim.kexNotNull(method.cm) -> {
                val value = call.arguments[0]
                val term = term { generate(KexBool()) }
                listOf(
                    state { term equality (value eq null) },
                    assume { term equality false }
                )
            }
            else -> nothing()
        }
    }

    private fun collectionIntrinsicsAdapter(method: Method, call: CallTerm, lhv: () -> Term): PredicateState = basic {
        when (method) {
            kim.kexForAll(method.cm) -> state {
                lhv() equality forAll(call.arguments[0], call.arguments[1], call.arguments[3])
            }
            in kim.kexContainsMethods(method.cm) -> {
                val (array, value) = call.arguments
                val start = const(0)
                val length = generate(KexInt())
                state {
                    length equality array.length()
                }
                state {
                    lhv() equality forAll(start, length) {
                        val index = value(KexInt(), "ind")
                        lambda(KexBool(), listOf(index)) {
                            array[index].load() eq value
                        }
                    }
                }
                val temp = generate(KexBool())
                state {
                    temp equality (length gt 0)
                }
                assume {
                    temp equality true
                }
            }
            kim.kexContains(method.cm) -> {
                val (array, value) = call.arguments
                val start = const(0)
                val length = generate(KexInt())
                state {
                    length equality array.length()
                }
                state {
                    lhv() equality forAll(start, length) {
                        val index = value(KexInt(), "ind")
                        lambda(KexBool(), listOf(index)) {
                            array[index].load() equls value
                        }
                    }
                }
                val temp = generate(KexBool())
                state {
                    temp equality (length gt 0)
                }
                assume {
                    temp equality true
                }
            }
            in kim.kexGenerateArrayMethods(method.cm) -> {
                state {
                    generateArray(lhv(), call.arguments[0], call.arguments[1])
                }
            }
            else -> nothing()
        }
    }
}