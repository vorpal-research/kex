package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.sbst.KexTool
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter


@ExperimentalSerializationApi
@InternalSerializationApi
fun main(args: Array<String>) {
    val timeout = if (args.size > 0) args[0].toInt() else 600
    val benchmarkPath = if (args.size > 1) args[1] else "/var/benchmarks"
    val actualPath = if (args.size > 2) args[2] else "/home/abdullin/workspace/JUGE/infrastructure/benchmarks_9th"
    val reader = InputStreamReader(System.`in`).buffered()
    reader.readLine() // skip name
    reader.readLine() // skip {
    val src = reader.readLine().split("=").lastOrNull() ?: ""
    val bin = reader.readLine().split("=").lastOrNull() ?: ""
    reader.readLine() // skip classes=(
    val classes = reader.readLine().trim().split(",")
    reader.readLine() // skip )
    reader.readLine() // skip
    reader.readLine() // skip classPath=(
    val classPath = reader.readLine().trim().split(",")
    reader.readLine() // skip )

    val input = buildString {
        appendLine("BENCHMARK")
        appendLine(src.replace(benchmarkPath, actualPath))
        appendLine(bin.replace(benchmarkPath, actualPath))
        appendLine(classPath.size)
        for (path in classPath) appendLine(path.replace(benchmarkPath, actualPath))
        appendLine(classes.size)
        for (klass in classes) {
            appendLine(timeout)
            appendLine(klass)
        }
    }

    val writer = PrintWriter(System.out)
    val tool = KexTool()
    val runTool = SBSTRunTool(tool, input.byteInputStream().reader(), writer)
    runTool.run()
}