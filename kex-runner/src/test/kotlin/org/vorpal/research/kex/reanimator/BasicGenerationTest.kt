package org.vorpal.research.kex.reanimator

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.KexRunnerTest
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class BasicGenerationTest : KexRunnerTest() {

    @Test
    fun testBasic() {
        val `class` = cm["$packageName/generation/BasicGenerationTests"]
        runPipelineOn(`class`)
    }

    @Test
    fun testBasicJava() {
        val `class` = cm["$packageName/generation/BasicJavaObjectGeneration"]
        runPipelineOn(`class`)
    }

    @Test
    fun testObjectGeneration() {
        val `class` = cm["$packageName/generation/ObjectGenerationTests"]
        runPipelineOn(`class`)
    }

    @Test
    fun testAbstractClassGeneration() {
        val `class` = cm["$packageName/generation/AbstractClassTests"]
        runPipelineOn(`class`)
    }
}