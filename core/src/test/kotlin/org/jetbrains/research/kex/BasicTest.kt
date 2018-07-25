package org.jetbrains.research.kex

import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BasicTest : KexTest() {
    val `class` = CM.getByName("$packageName/BasicTests")

    @Test
    fun testBasicReachability() {
        `class`.methods.forEach { _, method ->
            log.debug("Checking method $method")
            log.debug(method.print())

            val psa = getPSA(method)
            val checker = Checker(method, psa)

            getReachables(method).forEach { inst ->
                val result = checker.checkReachable(inst)
                assertTrue(result is Result.SatResult)

                inst as CallInst
                val assertionsArray = inst.args.first()
                val assertions = method.flatten()
                        .mapNotNull { it as? ArrayStoreInst }
                        .filter { it.arrayRef == assertionsArray }
                        .map { it.value }

                val model = (result as Result.SatResult).model
                log.debug("Acquired model: $model")
                log.debug("Checked assertions: $assertions")
                assertions.forEach {
                    val argTerm = TermFactory.getValue(it)
                    val modelValue = model.assignments[argTerm]
                    assertNotNull(modelValue)
                    assertTrue(
                            ((modelValue is ConstBoolTerm) && modelValue.value) ||
                                    (modelValue is ConstIntTerm) && modelValue.value > 0
                    )
                }
            }

            getUnreachables(method).forEach { inst ->
                val result = checker.checkReachable(inst)
                assertTrue(result is Result.UnsatResult)
            }
        }
    }
}