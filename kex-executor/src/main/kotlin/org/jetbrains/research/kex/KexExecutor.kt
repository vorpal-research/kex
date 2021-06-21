package org.jetbrains.research.kex

import org.jetbrains.research.kex.compile.JavaCompilerDriver
import org.jetbrains.research.kex.config.ExecutorCmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.util.getJunit
import org.jetbrains.research.kex.util.getPathSeparator
import org.jetbrains.research.kthelper.logging.log
import java.nio.file.Paths

fun main(args: Array<String>) {
    KexExecutor(args).main()
}

class KexExecutor(args: Array<String>) {
    private val cmd = ExecutorCmdConfig(args)
    private val properties = cmd.getCmdValue("config", "kex.ini")
    private val logName = cmd.getCmdValue("log", "kex-executor.log")

    fun main() {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        kexConfig.initLog(logName)

        val classPass = cmd.getCmdValue("classpath")!!.split(getPathSeparator()).map { Paths.get(it) }
        val testFiles = cmd.argList
        val output = Paths.get(cmd.getCmdValue("output", "kex-compiled"))

        val compiler = JavaCompilerDriver(listOfNotNull(*classPass.toTypedArray(), getJunit()), output)
        val files = compiler.compile(testFiles.map { Paths.get(it) })

        log.debug(cmd.getCmdValue("cp"))
        log.debug(files.joinToString(";"))
    }
}