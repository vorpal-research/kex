package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.sbst.KexTool
import java.io.InputStreamReader
import java.io.PrintWriter

@ExperimentalSerializationApi
@InternalSerializationApi
fun main(args: Array<String>) {
    val writer = PrintWriter(System.out)
    val reader = InputStreamReader(System.`in`)
    val tool = KexTool()
    val runTool = SBSTRunTool(tool, reader, writer)
    runTool.run()
}