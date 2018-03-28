package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kex.asm.JumpInstrumenter
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.writeJar
import uk.org.lidalia.sysoutslf4j.context.LogLevel
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J
import java.util.jar.JarFile

fun main(args: Array<String>) {
    SysOutOverSLF4J.sendSystemOutAndErrToSLF4J(LogLevel.DEBUG, LogLevel.ERROR)
    val log = loggerFor("org.jetbrains.research.kex.Main")

    val config = GlobalConfig
    val cmd = CmdConfig(args)
    val properties = cmd.getStringValue("properties", "system.properties")
    config.initialize(listOf(cmd, FileConfig(properties)))

    val jarName = config.getStringValue("jar")
    val packageName = config.getStringValue("package", "*")
    assert(jarName != null, cmd::printHelp)

    val jar = JarFile(jarName)
    val `package` = Package(packageName.replace('.', '/'))
    CM.parseJar(jar, `package`)

    log.debug("Running with jar ${jar.name} and package $`package`")
    for (it in CM.getConcreteClasses()) {
        for ((_, method) in it.methods) {
            if (!method.isAbstract()) JumpInstrumenter(method).visit()
        }
    }
    writeJar(jar, `package`, "instrumented")
}