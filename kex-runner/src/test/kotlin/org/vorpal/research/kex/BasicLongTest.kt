package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class BasicLongTest : KexRunnerTest("basic-long") {

    @Test
    fun testIcfpc2018() {
        val `class` = cm["$packageName/Icfpc2018Test"]
        testClassReachability(`class`)
    }

}