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

        var loops = LoopAnalysis(method)
        if (loops.isEmpty()) return

        LoopSimplifier.visit(method)
        loops = LoopAnalysis(method)
        loops.forEach {
            assertTrue { it.hasSinglePreheader }
            assertTrue { it.hasSingleLatch }
        }

        LoopDeroller.visit(method)
        loops = LoopAnalysis(method)
        assertTrue(loops.isEmpty())

        IRVerifier.visit(method)
    }

    @Test
    fun simpleLoopTest() {
        val `class` = CM.getByName("$packageName/LoopTests")
        for ((_, method) in `class`.methods) {
            checkLoops(method)
        }
    }

    @Test
    fun icfpcLoopTest() {
        val `class` = CM.getByName("$packageName/Icfpc2018Test")
        for ((_, method) in `class`.methods) {
            checkLoops(method)
        }
    }
}