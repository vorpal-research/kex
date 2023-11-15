package org.vorpal.research.kex.reanimator

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.KexRunnerTest
import kotlin.test.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class BasicGenerationTest : KexRunnerTest("basic-generation") {

    @Test
    fun testBasic() {
        val `class` = cm["${`package`.concretePackage}/generation/BasicGenerationTests"]
        runPipelineOn(`class`)
    }

    @Test
    fun testBasicJava() {
        val `class` = cm["${`package`.concretePackage}/generation/BasicJavaObjectGeneration"]
        runPipelineOn(`class`)
    }

    @Test
    fun testObjectGeneration() {
        val `class` = cm["${`package`.concretePackage}/generation/ObjectGenerationTests"]
        runPipelineOn(`class`)
    }

    @Test
    fun testAbstractClassGeneration() {
        val `class` = cm["${`package`.concretePackage}/generation/AbstractClassTests"]
        runPipelineOn(`class`)
    }
}
