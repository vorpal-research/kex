package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.LoopAnalysis
import kotlin.test.Test
import kotlin.test.assertTrue

class LoopDerollerTest : KexTest() {

    private fun checkLoops(method: Method) {
        if (method.isAbstract) return

        if (!method.hasLoops) return

        LoopSimplifier(cm).visit(method)
        var loops = LoopAnalysis(cm).invoke(method)
        for (loop in loops) {
            if (!loop.hasSinglePreheader) return
            if (!loop.hasSingleLatch) return
        }

        LoopDeroller(cm).visit(method)
        loops = LoopAnalysis(cm).invoke(method)
        assertTrue(loops.isEmpty())
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