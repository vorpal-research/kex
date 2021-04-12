package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method
import kotlin.test.Test
import kotlin.test.assertTrue

class LoopDerollerTest : KexTest() {

    private fun checkLoops(method: Method) {
        if (method.isAbstract) return

        var loops = LoopAnalysis(cm).invoke(method)
        if (loops.isEmpty()) return

        LoopSimplifier(cm).visit(method)
        loops = LoopAnalysis(cm).invoke(method)
        loops.forEach {
            assertTrue { it.hasSinglePreheader }
            assertTrue { it.hasSingleLatch }
        }

        LoopDeroller(cm).visit(method)
        loops = LoopAnalysis(cm).invoke(method)
        assertTrue(loops.isEmpty())

//        IRVerifier(cm).visit(method)
    }

    @Test
    fun simpleLoopTest() {
        val `class` = cm["$packageName/LoopTests"]
        for (method in `class`.allMethods) {
            checkLoops(method)
        }
    }

    @Test
    fun icfpcLoopTest() {
        val `class` = cm["$packageName/Icfpc2018Test"]
        for (method in `class`.allMethods) {
            checkLoops(method)
        }
    }
}