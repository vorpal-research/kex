package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.KexRunnerTest
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class BasicGenerationTest : KexRunnerTest() {
    private var oldApiGenerationValue: Boolean = false

    @BeforeTest
    fun initConfig() {
        oldApiGenerationValue = kexConfig.getBooleanValue("recovering", "apiGeneration", false)
        RuntimeConfig.setValue("recovering", "apiGeneration", true)
    }

    @AfterTest
    fun restoreConfig() {
        RuntimeConfig.setValue("recovering", "apiGeneration", oldApiGenerationValue)
    }

    @Test
    fun testBasic() {
        val `class` = cm.getByName("$packageName/generation/BasicGenerationTests")
        runPipelineOn(`class`)
    }

    @Test
    fun testBasicJava() {
        val `class` = cm.getByName("$packageName/generation/BasicJavaObjectGeneration")
        runPipelineOn(`class`)
    }

    @Test
    fun testObjectGeneration() {
        val `class` = cm.getByName("$packageName/generation/ObjectGenerationTests")
        runPipelineOn(`class`)
    }
}