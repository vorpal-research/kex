package org.jetbrains.research.kex

import kotlin.test.Test

class BasicLongTest : KexTest() {

    @Test
    fun testIcfpc2018() {
        val `class` = cm.getByName("$packageName/Icfpc2018Test")
        testClassReachability(`class`)
    }

}