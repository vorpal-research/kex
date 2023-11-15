package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class BasicTest : KexRunnerTest("basic") {

    @Test
    fun testBasicReachability() {
        val `class` = cm["${`package`.concretePackage}/BasicTests"]
        testClassReachability(`class`)
    }

}
