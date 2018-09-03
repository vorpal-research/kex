package org.jetbrains.research.kex

import org.jetbrains.research.kfg.CM
import kotlin.test.Test

class ObjectTest : KexTest() {

    @Test
    fun testBasicReachability() {
        val `class` = CM.getByName("$packageName/ObjectTests")
        testClassReachability(`class`)
    }

    @Test
    fun testJavaBasicReachability() {
        val `class` = CM.getByName("$packageName/ObjectJavaTests")
        testClassReachability(`class`)
    }

}