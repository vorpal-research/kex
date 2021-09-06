package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.config.RuntimeConfig
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi

class IntrinsicsTest : KexRunnerTest() {

    @Test
    fun testBasicReachability() {
        val cfg = RuntimeConfig
        val oldSlicingConfig = cfg.getBooleanValue("smt", "slicing", true)
        RuntimeConfig.setValue("smt", "slicing", false)

        val `class` = cm["$packageName/IntrinsicsTest"]
        testClassReachability(`class`)

        RuntimeConfig.setValue("smt", "slicing", oldSlicingConfig)
    }
}