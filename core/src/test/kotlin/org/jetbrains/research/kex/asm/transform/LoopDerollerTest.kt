package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.ConcreteClass
import kotlin.test.Test
import kotlin.test.assertTrue

class LoopDerollerTest : KexTest() {
    val className = "${packageName}/LoopTests"
    val `class` = CM.getByName(className)

    init {
        assert(`class` is ConcreteClass) { log.error("Could not load class `$className`") }
    }

    @Test
    fun loopTest() {
        for ((_, method) in `class`.methods) {
            if (method.isAbstract()) continue

            val la = LoopAnalysis(method)
            la.visit()
            if (la.loops.isEmpty()) continue

            LoopSimplifier(method).visit()
            la.visit()
            la.loops.forEach {
                assertTrue { it.hasSinglePreheader() }
                assertTrue { it.hasSingleLatch() }
            }

            val deroller = LoopDeroller(method)
            deroller.visit()

            la.visit()
            assertTrue(la.loops.isEmpty())

            IRVerifier(method).visit()
        }
    }
}