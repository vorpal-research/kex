package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class ArrayLongTest : KexRunnerTest() {
    @Test
    fun testArrays() {
        val `class` = cm["$packageName/ArrayLongTests"]
        testClassReachability(`class`)
    }
}