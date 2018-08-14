package org.jetbrains.research.kex

import org.jetbrains.research.kfg.CM
import kotlin.test.Test

class BasicTest : KexTest() {

    @Test
    fun testBasicReachability() {
        val `class` = CM.getByName("$packageName/BasicTests")
        testClassReachability(`class`)
    }
}