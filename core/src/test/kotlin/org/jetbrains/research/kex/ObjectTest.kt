package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.RuntimeConfig
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
        val oldValue = RuntimeConfig.getBooleanValue("smt", "ps-inlining", false)
        RuntimeConfig.setValue("smt", "ps-inlining", false)
        testClassReachability(`class`)
        RuntimeConfig.setValue("smt", "ps-inlining", oldValue)
    }

}