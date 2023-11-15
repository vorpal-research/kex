package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class ObjectTest : KexRunnerTest("object") {

    @Test
    fun testBasicReachability() {
        val `class` = cm["${`package`.concretePackage}/ObjectTests"]
        testClassReachability(`class`)
    }

    @Test
    fun testJavaBasicReachability() {
        val `class` = cm["${`package`.concretePackage}/ObjectJavaTests"]
        testClassReachability(`class`)
    }

}
