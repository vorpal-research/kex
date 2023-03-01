package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.emptyState
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.collection.dequeOf

class KexIntrinsicsAdapter : RecollectingTransformer<KexIntrinsicsAdapter> {
    override val builders = dequeOf(StateBuilder())
    private val kim = MethodManager.KexIntrinsicManager

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm

        val method = call.method
        val cm = method.cm
        val ps = when (call.method.klass) {
            kim.assertionsIntrinsics(cm) -> assertionIntrinsicsAdapter(method, call)
            kim.collectionIntrinsics(cm) -> collectionIntrinsicsAdapter(method, call) { predicate.lhv }
            kim.objectIntrinsics(cm) -> objectIntrinsicsAdapter(method, call) { predicate.lhv }
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
                val assumption = call.arguments[0]
                assume { assumption equality true }
            }
            kim.kexNotNull(method.cm) -> {
                val value = call.arguments[0]
                val term = term { generate(KexBool) }
                listOf(
                    state { term equality (value eq null) },
                    assume { term equality false }
                )
            }
            kim.kexAssert(method.cm) -> {
                val assertion = call.arguments[0]
                require { assertion equality true }
            }
            else -> nothing()
        }
    }

    private fun collectionIntrinsicsAdapter(method: Method, call: CallTerm, lhv: () -> Term): PredicateState = basic {
        when (method) {
            kim.kexForAll(method.cm) -> state {
                lhv() equality forAll(call.arguments[0], call.arguments[1], call.arguments[2])
            }
            in kim.kexContainsMethods(method.cm) -> {
                val (array, value) = call.arguments
                val length = generate(KexInt)
                state {
                    length equality array.length()
                }
                state {
                    lhv() equality (value `in` array)
                }
                val temp = generate(KexBool)
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
                val length = generate(KexInt)
                state {
                    length equality array.length()
                }
                state {
                    lhv() equality exists(start, length) {
                        val index = value(KexInt, "ind")
                        lambda(KexBool, listOf(index)) {
                            array[index].load() equls value
                        }
                    }
                }
                val temp = generate(KexBool)
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

    private fun objectIntrinsicsAdapter(method: Method, call: CallTerm, lhv: () -> Term): PredicateState = basic {
        when (method) {
            kim.kexEquals(method.cm) -> state { lhv() equality (call.arguments[0].equls(call.arguments[1]))  }
            else -> nothing()
        }
    }
}
