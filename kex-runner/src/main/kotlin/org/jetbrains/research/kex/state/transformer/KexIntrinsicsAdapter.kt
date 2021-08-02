package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.ktype.KexVoid
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.basic
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.LambdaTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.util.asList
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kthelper.collection.buildList
import org.jetbrains.research.kthelper.logging.log

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
        val predicates = when (call.method.klass) {
            kim.assertionsIntrinsics(cm) -> assertionIntrinsicsAdapter(method, call)
            kim.collectionIntrinsics(cm) -> collectionIntrinsicsAdapter(method, call)
            else -> listOf()
        }
        for (assertion in predicates) {
            currentBuilder += assertion
        }
        return when {
            predicates.isEmpty() -> predicate
            else -> nothing()
        }
    }

    private fun assertionIntrinsicsAdapter(method: Method, call: CallTerm): List<Predicate> = when (method) {
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
        else -> emptyList()
    }

    private fun collectionIntrinsicsAdapter(method: Method, call: CallTerm): List<Predicate> = when (method) {
        kim.kexForEach(method.cm) -> state {
            forEach(call.arguments[0], call.arguments[1], call.arguments[3])
        }
        kim.kexArrayCopy(method.cm) -> state {
            forEach(const(0), call.arguments[4]) {
                val index = value(KexInt(), "ind")
                lambda(KexVoid(), listOf(index)) {
                    val srcIndex = value(KexInt(), "srcInd")
                    val dstIndex = value(KexInt(), "dstInd")
                    val copyValue = value(KexInt(), "value")
                    basic {
                        state {
                            srcIndex equality (index + call.arguments[1])
                        }
                        state {
                            dstIndex equality (index + call.arguments[3])
                        }
                        state {
                            copyValue equality call.arguments[0][srcIndex].load()
                        }
                        state {
                            call.arguments[2][dstIndex].store(copyValue)
                        }
                    }
                }
            }
        }
        in kim.kexContainsMethods(method.cm) -> state {
            val (array, value) = call.arguments
            val start = const(0)
            val end = array.length()
            forEach(start, end) {
                val index = value(KexInt(), "ind")
                lambda(KexBool(), listOf(index)) {
                    val currentElement = value((array.type as KexArray).element, "current")
                    val result = value(KexBool(), "equality")
                    basic {
                        state {
                            result equality (currentElement eq value)
                        }
                    }
                }
            }
        }
        kim.kexContains(method.cm) -> state {
            val (array, value) = call.arguments
            val start = const(0)
            val end = array.length()
            forEach(start, end) {
                val index = value(KexInt(), "ind")
                lambda(KexBool(), listOf(index)) {
                    val currentElement = value((array.type as KexArray).element, "current")
                    val result = value(KexBool(), "equality")
                    basic {
                        state {
                            result equality (currentElement equls value)
                        }
                    }
                }
            }
        }
        else -> unreachable { log.error("Unknown intrinsics method $method") }
    }.asList()
}