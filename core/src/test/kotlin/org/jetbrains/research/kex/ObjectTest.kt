package org.jetbrains.research.kex

import kotlin.test.Test

class ObjectTest : KexTest() {

    @Test
    fun testBasicReachability() {
        val `class` = cm.getByName("$packageName/ObjectTests")
        testClassReachability(`class`)
    }

    @Test
    fun testJavaBasicReachability() {
        val `class` = cm.getByName("$packageName/ObjectJavaTests")
        testClassReachability(`class`)
    }

}