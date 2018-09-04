package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.analysis.IRVerifier
import kotlin.test.Test

class MethodInlinerTest : KexTest() {

    @Test
    fun testIRMethodInliner() {
        for (`class` in CM.getConcreteClasses()) {
            for (method in `class`.methods.values) {
                MethodInliner.visit(method)
                IRVerifier.visit(method)
            }
        }
    }
}