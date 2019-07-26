package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kex.config.RuntimeConfig
import kotlin.test.*

class AnnotationsTest : KexTest() {
    companion object {
        init {
            (AnnotationManager.defaultLoader as ExternalAnnotationsLoader).
                    loadFrom(AnnotationsTest::class.java.getResource("annotations.2.xml"))
        }
    }

    private var oldInliningConfig = RuntimeConfig.getBooleanValue("smt", "ps-inlining", true)

    @BeforeTest
    fun before() {
        RuntimeConfig.setValue("smt", "ps-inlining", false)
    }

    @AfterTest
    fun after() {
        RuntimeConfig.setValue("smt", "ps-inlining", oldInliningConfig)
    }

    @Test
    fun `Test reachability with annotations`() {
        val `class` = cm.getByName("$packageName/NotAnnotatedMethods")
        testClassReachability(`class`)
    }
}
