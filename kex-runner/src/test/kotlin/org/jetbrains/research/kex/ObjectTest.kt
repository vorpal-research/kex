package org.jetbrains.research.kex

import kotlin.test.Test

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