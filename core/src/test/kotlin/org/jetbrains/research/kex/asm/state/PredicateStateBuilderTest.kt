package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.UnreachableInst
import org.jetbrains.research.kthelper.graph.NoTopologicalSortingException
import org.junit.Assert.assertNotNull
import kotlin.test.Test

class PredicateStateBuilderTest : KexTest() {

    private fun performPSA(method: Method): PredicateStateBuilder {
        if (method.hasLoops) {
            LoopSimplifier(cm).visit(method)
            LoopDeroller(cm).visit(method)
        }

        val psa = PredicateStateBuilder(method)
        psa.init()
        return psa
    }

    @Test
    fun testSimplePSA() {
//        for (`class` in cm.concreteClasses) {
//            for (method in `class`.allMethods) {
//                if (method.isAbstract) continue
//
//                val psa = try {
//                    performPSA(method)
//                } catch (e: NoTopologicalSortingException) {
//                    continue
//                }
//
//                val catchBlocks = method.catchBlocks
//                method.filter { it !in catchBlocks }
//                        .flatten()
//                        .filter { it !is UnreachableInst }
//                        .forEach {
//                    assertNotNull(psa.getInstructionState(it))
//                }
//            }
//        }
    }
}