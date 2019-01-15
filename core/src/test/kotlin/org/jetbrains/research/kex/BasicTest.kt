package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.RuntimeConfig
import kotlin.test.Test

class BasicTest : KexTest() {

    @Test
    fun testBasicReachability() {
        val cfg = RuntimeConfig
        val oldSlicingConfig = cfg.getBooleanValue("smt", "slicing", true)
        RuntimeConfig.setValue("smt", "slicing", false)

        val `class` = cm.getByName("$packageName/BasicTests")
        testClassReachability(`class`)

        RuntimeConfig.setValue("smt", "slicing", oldSlicingConfig)
    }

    @Test
    fun testIcfpc2018() {
        val `class` = cm.getByName("$packageName/Icfpc2018Test")
        testClassReachability(`class`)
    }
}