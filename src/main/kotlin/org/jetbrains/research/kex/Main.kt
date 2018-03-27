package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.FileConfig
import uk.org.lidalia.sysoutslf4j.context.LogLevel
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J

fun main(args: Array<String>) {
    SysOutOverSLF4J.sendSystemOutAndErrToSLF4J(LogLevel.DEBUG, LogLevel.ERROR)

    val config = GlobalConfig
    val cmd = CmdConfig(args)
    val properties = cmd.getStringValue("properties", "system.properties")
    config.initialize(listOf(cmd, FileConfig(properties)))

    val jarName = config.getStringValue("jar", "")
    val packageName = config.getStringValue("package", "")
    assert(jarName.isEmpty() or packageName.isEmpty(), cmd::printHelp)
}