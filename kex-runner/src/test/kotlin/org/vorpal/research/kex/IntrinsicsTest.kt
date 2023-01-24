package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi

class IntrinsicsTest : KexRunnerTest("intrinsic") {

    @Test
    fun testBasicReachability() {
        val `class` = cm["$packageName/IntrinsicsTest"]
        testClassReachability(`class`)
    }
}