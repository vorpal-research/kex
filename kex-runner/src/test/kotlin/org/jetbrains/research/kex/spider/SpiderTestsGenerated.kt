package org.jetbrains.research.kex.spider

import org.junit.Test

// DO NOT MODIFY THIS CODE MANUALLY!
// You should use ./generateTests.kt to do it
// Some of these tests are depend by inliner.depth parameter in kex.ini

class SpiderTestsGenerated {
    @Test
    fun testBuilderLibrary() {
        SpiderTestRunner("builderLibrary").runTest()
    }

    @Test
    fun testComparators() {
        SpiderTestRunner("comparators").runTest()
    }

    @Test
    fun testComputer() {
        SpiderTestRunner("computer").runTest()
    }

    @Test
    fun testOkhttp3() {
        SpiderTestRunner("okhttp3").runTest()
    }

    @Test
    fun testVk() {
        SpiderTestRunner("vk").runTest()
    }
}
