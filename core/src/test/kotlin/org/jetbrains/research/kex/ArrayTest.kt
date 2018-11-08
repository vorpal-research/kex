package org.jetbrains.research.kex

import kotlin.test.Test

class ArrayTest : KexTest() {
    @Test
    fun testArrays() {
        val `class` = cm.getByName("$packageName/ArrayTests")
        testClassReachability(`class`)
    }
}