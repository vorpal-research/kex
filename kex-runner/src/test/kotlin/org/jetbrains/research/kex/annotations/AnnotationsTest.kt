package org.jetbrains.research.kex.annotations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.KexRunnerTest
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class AnnotationsTest : KexRunnerTest() {
    companion object {
        init {
            (AnnotationManager.defaultLoader as ExternalAnnotationsLoader).
                    loadFrom(AnnotationsTest::class.java.getResource("annotations.2.xml") ?: unreachable {
                        log.error("Could not find test annotations")
                    })
        }
    }

    private var oldInliningConfig = RuntimeConfig.getBooleanValue("smt", "psInlining", true)

    @BeforeTest
    fun before() {
        RuntimeConfig.setValue("smt", "psInlining", false)
    }

    @AfterTest
    fun after() {
        RuntimeConfig.setValue("smt", "psInlining", oldInliningConfig)
    }

    @Test
    fun `Test reachability with annotations`() {
        val `class` = cm["$packageName/NotAnnotatedMethods"]
        testClassReachability(`class`)
    }
}
