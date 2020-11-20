package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.statistics.Statistics
import java.io.File
import kotlin.math.round
import kotlin.system.measureTimeMillis

@ExperimentalSerializationApi
@InternalSerializationApi
fun main(args: Array<String>) {
    val logFile = File("kex-statistics-concolic-CGS.csv")
    Statistics.makeLogFile(logFile)
    val time = measureTimeMillis {  Kex(args).main() }
    println("${round(time.toFloat() / (1000.0))} seconds")
    Statistics.methodHolder.values.forEach { it.log() }
}