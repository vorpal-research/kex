package org.jetbrains.research.kex

import org.jetbrains.research.kfg.CM
import kotlin.test.Test

class ArrayTest : KexTest() {
    @Test
    fun testArrays() {
        val `class` = CM.getByName("$packageName/ArrayTests")
        testClassReachability(`class`)
    }
}