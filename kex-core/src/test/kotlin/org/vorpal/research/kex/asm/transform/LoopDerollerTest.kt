package org.vorpal.research.kex.asm.transform

import org.vorpal.research.kex.KexTest
import org.vorpal.research.kfg.analysis.LoopSimplifier
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.LoopAnalysis
import kotlin.test.Test
import kotlin.test.assertTrue

class LoopDerollerTest : KexTest("loop-deroller") {

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
        val `class` = cm["${`package`.concretePackage}/LoopTests"]
        for (method in `class`.allMethods) {
            checkLoops(method)
        }
    }

    @Test
    fun treeMapLoopTest() {
        val `class` = cm["${`package`.concretePackage}/javadebug/TreeMap"]
        for (method in `class`.allMethods) {
            checkLoops(method)
        }
    }
}
