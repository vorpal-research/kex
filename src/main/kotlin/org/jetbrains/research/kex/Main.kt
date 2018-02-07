package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.driver.SootDriver
import org.slf4j.LoggerFactory
import soot.jimple.JimpleBody
import soot.shimple.Shimple
import soot.shimple.ShimpleBody

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("main")
    val cmd = CmdConfig(args)
    val propertiesFile = cmd.getStringValue("properties", "system.properties")
    GlobalConfig.initialize( listOf(cmd, FileConfig(propertiesFile)) )

    val driver = SootDriver.instance
    val jar = GlobalConfig.instance.getStringValue("jar", "")
    val mainClass = GlobalConfig.instance.getStringValue("main", "")

    if (jar.isEmpty() or mainClass.isEmpty()) {
        cmd.printHelp()
        System.exit(1)
    }

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