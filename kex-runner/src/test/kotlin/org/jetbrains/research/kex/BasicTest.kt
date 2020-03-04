package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.RuntimeConfig
import kotlin.test.Test

class BasicTest : KexRunnerTest() {

    @Test
    fun testBasicReachability() {
        val cfg = RuntimeConfig
        val oldSlicingConfig = cfg.getBooleanValue("smt", "slicing", true)
        RuntimeConfig.setValue("smt", "slicing", false)

        val `class` = cm["$packageName/BasicTests"]
        testClassReachability(`class`)

        RuntimeConfig.setValue("smt", "slicing", oldSlicingConfig)
    }

}