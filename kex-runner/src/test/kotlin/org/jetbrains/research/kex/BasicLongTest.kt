package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class BasicLongTest : KexRunnerTest() {

    @Test
    fun testIcfpc2018() {
        val `class` = cm["$packageName/Icfpc2018Test"]
        testClassReachability(`class`)
    }

}