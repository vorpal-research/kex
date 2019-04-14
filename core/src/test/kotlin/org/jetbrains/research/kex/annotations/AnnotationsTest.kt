package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.KexTest
import org.junit.Test

class AnnotationsTest : KexTest() {

    @Test
    fun `So lets test it`() {
        (AnnotationManager.defaultLoader as ExternalAnnotationsLoader).apply {
            loadFrom(AnnotationsTest::class.java.getResource("annotations.2.xml"))
        }
        val `class` = cm.getByName("$packageName/NotAnnotatedMethods")
        testClassReachability(`class`)
    }

}
