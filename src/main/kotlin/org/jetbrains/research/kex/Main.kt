package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.PropertyConfig
import org.jetbrains.research.kex.driver.SootDriver
import soot.jimple.JimpleBody
import soot.shimple.Shimple
import soot.shimple.ShimpleBody

fun main(args: Array<String>) {
    val cmd = CmdConfig(args)
    val propertiesFile = cmd.getStringValue("properties", "system.properties")
    GlobalConfig.initialize(
            listOf(
                    cmd,
                    PropertyConfig(propertiesFile)
            )
    )

    val driver = SootDriver.instance
    val jar = GlobalConfig.instance.getStringValue("jar", "")
    val mainClass = GlobalConfig.instance.getStringValue("main", "")

    if (jar.isEmpty() or mainClass.isEmpty()) {
        cmd.printHelp()
        System.exit(1)
    }

    println("Analyzing package $jar with main class $mainClass")
    driver.setup(jar, mainClass)
    var classCount = 0
    var methodCount = 0
    driver.getClasses()
            .filter { !it.name.matches(Regex("java.*|kotlin.*|sun.*")) }
            .forEach { sc ->
                println(sc.name)
                sc.methods.forEach {
                    if (it.hasActiveBody()) {
                        val shimple = when (it.activeBody) {
                            is ShimpleBody -> it.activeBody as ShimpleBody
                            is JimpleBody -> Shimple.v().newBody(it)
                            else -> {
                                println("No body for ${it.name}")
                                Shimple.v().newBody(it)
                            }
                        }
                        println(shimple)
                        println()
                        ++methodCount
                    }
                }
                ++classCount
            }
    println("Total $classCount classes; $methodCount methods")
}