package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.ExecutorCmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig

fun main(args: Array<String>) {
    KexExecutor(args).main()
}

class KexExecutor(args: Array<String>) {
    private val cmd = ExecutorCmdConfig(args)
    private val properties = cmd.getCmdValue("config", "kex.ini")

    fun main() {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        val logName = kexConfig.getStringValue("kex", "log", "kex-executor.log")
        kexConfig.initLog(logName)

        val klass = cmd.getCmdValue("class")!!
        val method = cmd.getCmdValue("method")!!


        val loader = this.javaClass.classLoader
        val javaClass = loader.loadClass(klass)
        val instance = javaClass.newInstance()

        val javaMethod = javaClass.getMethod(method)
        javaMethod.invoke(instance)
    }
}