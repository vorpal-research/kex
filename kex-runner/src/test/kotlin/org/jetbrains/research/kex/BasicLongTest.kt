package org.jetbrains.research.kex

import kotlin.test.Test

class BasicLongTest : KexRunnerTest() {

    @Test
    fun testIcfpc2018() {
        val `class` = cm["$packageName/Icfpc2018Test"]
        testClassReachability(`class`)
    }

}