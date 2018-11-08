package org.jetbrains.research.kex

import kotlin.test.Test

class BasicTest : KexTest() {

    @Test
    fun testBasicReachability() {
        val `class` = cm.getByName("$packageName/BasicTests")
        testClassReachability(`class`)
    }

    @Test
    fun testIcfpc2018() {
        val `class` = cm.getByName("$packageName/Icfpc2018Test")
        testClassReachability(`class`)
    }
}