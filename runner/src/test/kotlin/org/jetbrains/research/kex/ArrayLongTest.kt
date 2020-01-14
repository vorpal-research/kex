package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.RuntimeConfig
import kotlin.test.Test

class ArrayLongTest : KexRunnerTest() {
    @Test
    fun testArrays() {
        val cfg = RuntimeConfig
        val oldSlicingConfig = cfg.getBooleanValue("smt", "slicing", true)
        RuntimeConfig.setValue("smt", "slicing", false)

        val `class` = cm.getByName("$packageName/ArrayLongTests")
        testClassReachability(`class`)

        RuntimeConfig.setValue("smt", "slicing", oldSlicingConfig)
    }
}