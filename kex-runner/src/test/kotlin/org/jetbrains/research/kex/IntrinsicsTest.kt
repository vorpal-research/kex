package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi

class IntrinsicsTest : KexRunnerTest() {

    @Test
    fun testBasicReachability() {
        val `class` = cm["$packageName/IntrinsicsTest"]
        testClassReachability(`class`)
    }
}