package org.jetbrains.research.kex.concolic

import org.jetbrains.research.kex.KexRunnerTest
import kotlin.math.round
import kotlin.system.measureTimeMillis
import kotlin.test.Test

class BasicLongTest : KexRunnerTest() {

    @Test
    fun testIcfpc2018() {
        val `class` = cm["$packageName/Icfpc2018Test"]
        val time = measureTimeMillis {  concolic(`class`) }
        println("${round(time.toFloat() / (1000 * 60))} minutes")
    }

}