package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kfg.CM
import kotlin.test.Test

class BasicTest : KexTest() {

    @Test
    fun testBasicReachability() {
        val `class` = CM.getByName("$packageName/BasicTests")
        testClassReachability(`class`)
    }

// Don't run this because it's too long
    @Test
    fun testIcfpc2018() {
        // Don't check any assertions during in this tests, so can use slicing
        RuntimeConfig.setValue("smt", "slicing", true)

        val `class` = CM.getByName("$packageName/Icfpc2018Test")
        testClassReachability(`class`)
    }
}