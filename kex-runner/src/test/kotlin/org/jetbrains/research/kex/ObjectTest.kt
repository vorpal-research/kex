package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class ObjectTest : KexRunnerTest() {

    @Test
    fun testBasicReachability() {
        val `class` = cm["$packageName/ObjectTests"]
        testClassReachability(`class`)
    }

    @Test
    fun testJavaBasicReachability() {
        val `class` = cm["$packageName/ObjectJavaTests"]
        testClassReachability(`class`)
    }

}