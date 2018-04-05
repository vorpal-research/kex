package org.jetbrains.research.kex

import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kex.asm.JumpInstrumenter
import org.jetbrains.research.kex.runner.CoverageRunner
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.writeClass
import org.jetbrains.research.kfg.util.writeJar
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

fun main(args: Array<String>) {
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
    val target = File("instrumented/")
    writeJar(jar, `package`, target)

    for (`class` in CM.getConcreteClasses()) {
        for ((_, method) in `class`.methods) {
            if (!method.isAbstract() && method.name != "<init>") {
                val instrumenter = JumpInstrumenter(method)
                instrumenter.visit()
                writeClass(`class`, "${target.canonicalPath}/${`class`.getFullname()}.class")
                val loader = URLClassLoader(arrayOf(target.toURI().toURL()))
                CoverageRunner(method, loader).run()
                instrumenter.insertedInsts.forEach { it.parent?.remove(it) }
                writeClass(`class`, "${target.canonicalPath}/${`class`.getFullname()}.class")
            }
        }
    }
}