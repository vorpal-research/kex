package org.jetbrains.research.kex.concolic

import org.jetbrains.research.kex.KexRunnerTest
import org.jetbrains.research.kex.config.RuntimeConfig
import kotlin.math.round
import kotlin.system.measureTimeMillis
import kotlin.test.Test

class ArrayTest : KexRunnerTest() {
    @Test
    fun testArrays() {
        val `class` = cm["$packageName/ArrayTests"]
        val time = measureTimeMillis {  concolic(`class`) }
        println("${round(time.toFloat() / (1000 * 60))} minutes")
    }
}