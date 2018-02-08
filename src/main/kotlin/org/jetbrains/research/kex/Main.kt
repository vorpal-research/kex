package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.driver.SootDriver
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kex.util.assert
import soot.jimple.JimpleBody
import soot.shimple.Shimple
import soot.shimple.ShimpleBody
import uk.org.lidalia.sysoutslf4j.context.LogLevel
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J

fun main(args: Array<String>) {
    SysOutOverSLF4J.sendSystemOutAndErrToSLF4J(LogLevel.DEBUG, LogLevel.ERROR) // redirect all output from libraries to slf4j

    val log = loggerFor("main")
    val cmd = CmdConfig(args)
    val propertiesFile = cmd.getStringValue("properties", "system.properties")
    GlobalConfig.initialize( listOf(cmd, FileConfig(propertiesFile)) )

    val driver = SootDriver.instance
    val jar = GlobalConfig.instance.getStringValue("jar", "")
    val mainClass = GlobalConfig.instance.getStringValue("main", "")

    assert(jar.isEmpty().or(mainClass.isEmpty()), cmd::printHelp)

    driver.setup(jar, mainClass)
    driver.getClasses()
            .filter { !it.name.matches(Regex("java.*|kotlin.*|sun.*")) }
            .forEach { sc ->
                sc.methods.forEach {
                    if (it.hasActiveBody()) {
                        val shimple = when (it.activeBody) {
                            is ShimpleBody -> it.activeBody as ShimpleBody
                            is JimpleBody -> Shimple.v().newBody(it)
                            else -> {
                                log.warn("No body for ${it.name}")
                                Shimple.v().newBody(it)
                            }
                        }
                    }
                }
            }
}