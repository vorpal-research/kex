package org.jetbrains.research.kex

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlin.math.round
import kotlin.system.measureTimeMillis

@ImplicitReflectionSerializer
fun main(args: Array<String>) {
    val time = measureTimeMillis {  Kex(args).main() }
    println("${round(time.toFloat() / (1000.0))} seconds")
}