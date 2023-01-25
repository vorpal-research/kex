package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class ArrayTest : KexRunnerTest("array") {
    @Test
    fun testArrays() {
        val `class` = cm["$packageName/ArrayTests"]
        testClassReachability(`class`)

    }
}