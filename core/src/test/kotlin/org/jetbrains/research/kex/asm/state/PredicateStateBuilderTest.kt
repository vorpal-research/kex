package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.UnreachableInst
import org.junit.Assert.assertNotNull
import kotlin.test.Test

class PredicateStateBuilderTest : KexTest() {

    private fun performPSA(method: Method): PredicateStateBuilder {
        val loops = LoopAnalysis(method)
        if (loops.isNotEmpty()) {
            LoopSimplifier.visit(method)
            LoopDeroller.visit(method)
        }

        val psa = PredicateStateBuilder(method)
        psa.init()
        return psa
    }

    @Test
    fun testSimplePSA() {
        for (`class` in CM.getConcreteClasses()) {
            for ((_, method) in `class`.methods) {
                if (method.isAbstract) continue

                val psa = performPSA(method)

                val catchBlocks = method.catchBlocks
                method.filter { it !in catchBlocks }
                        .flatten()
                        .filter { it !is UnreachableInst }
                        .forEach {
                    assertNotNull(psa.getInstructionState(it))
                }
            }
        }
    }
}