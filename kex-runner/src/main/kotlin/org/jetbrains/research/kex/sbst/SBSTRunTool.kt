package org.jetbrains.research.kex

import org.jetbrains.research.kex.sbst.SBSTProtocol
import org.jetbrains.research.kex.sbst.Tool
import java.io.File
import java.io.Reader
import java.io.Writer
import java.util.*


class SBSTRunTool(private val tool: Tool, input: Reader, output: Writer) {
    private val channel: SBSTProtocol

    init {
        channel = SBSTProtocol(input, output)
    }

    fun run() {
        channel.token("BENCHMARK")
        val src = channel.directory()
        val bin = channel.directory()
        val n = channel.number()
        val classPath = ArrayList<File>()
        for (i in 0 until n) {
            classPath.add(channel.directoryJarFile())
        }
        tool.initialize(src, bin, classPath)
        val m = channel.number()
        if (tool.getExtraClassPath().isNotEmpty()) {
            channel.emit("CLASSPATH")
            val extraCP: List<File> = tool.getExtraClassPath()
            val k = extraCP.size
            channel.emit(k)
            for (file in extraCP) {
                channel.emit(file)
            }
        }
        channel.emit("READY")
        for (i in 0 until m) {
            val timeBudget = channel.longNumber()
            val cName: String = channel.className()
            tool.run(cName, timeBudget)
            channel.emit("READY")
        }
    }
}