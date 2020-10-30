package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlin.math.round
import kotlin.system.measureTimeMillis

@ExperimentalSerializationApi
@InternalSerializationApi
fun main(args: Array<String>) {
    val time = measureTimeMillis {  Kex(args).main() }
    println("${round(time.toFloat() / (1000.0))} seconds")
}