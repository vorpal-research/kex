package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method
import kotlin.test.Test
import kotlin.test.assertTrue

class LoopDerollerTest : KexTest() {

    private fun checkLoops(method: Method) {
        if (method.isAbstract) return

        val la = LoopAnalysis(method)
        la.visit()
        if (la.loops.isEmpty()) return

        LoopSimplifier(method).visit()
        la.visit()
        la.loops.forEach {
            assertTrue { it.hasSinglePreheader }
            assertTrue { it.hasSingleLatch }
        }

        val deroller = LoopDeroller(method)
        deroller.visit()

        la.visit()
        assertTrue(la.loops.isEmpty())

        IRVerifier(method).visit()
    }

    @Test
    fun simpleLoopTest() {
        val `class` = CM.getByName("${packageName}/LoopTests")
        for ((_, method) in `class`.methods) {
            checkLoops(method)
        }
    }

    @Test
    fun icfpcLoopTest() {
        val `class` = CM.getByName("${packageName}/Icfpc2018Test")
        for ((_, method) in `class`.methods) {
            checkLoops(method)
        }
    }
}