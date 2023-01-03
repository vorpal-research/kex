package org.vorpal.research.kex

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.sbst.SymbolicKexTool
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
fun main() {
    val writer = PrintWriter(System.out)
    val reader = InputStreamReader(System.`in`)
    val tool = SymbolicKexTool()
    val runTool = SBSTRunTool(tool, reader, writer)
    runTool.run()
}
