package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kfg.analysis.IRVerifier
import kotlin.test.Test

class MethodInlinerTest : KexTest() {

    @Test
    fun testIRMethodInliner() {
        for (`class` in cm.concreteClasses) {
            for (method in `class`.methods.values) {
                MethodInliner(cm).visit(method)
                IRVerifier(cm).visit(method)
            }
        }
    }
}