package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.analysis.MethodChecker
import org.jetbrains.research.kex.asm.analysis.RandomChecker
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.JarUtils
import org.jetbrains.research.kfg.util.classLoader
import org.jetbrains.research.kfg.visitor.Pipeline
import java.io.File
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val config = GlobalConfig
    val cmd = CmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    config.initialize(cmd, RuntimeConfig, FileConfig(properties))

    val jarName = cmd.getCmdValue("jar")
    val packageName = cmd.getCmdValue("package", "*")
    require(jarName != null, cmd::printHelp)

    val jar = JarFile(jarName)
    val jarLoader = jar.classLoader
    val `package` = Package(packageName.replace('.', '/'))
    CM.parseJar(jar, `package`, Flags.readAll)

    log.debug("Running with jar ${jar.name} and package $`package`")
    val target = File("instrumented/")
    JarUtils.writeClassesToTarget(jar, target, `package`, true) // write all classes to target, so they will be seen by ClassLoader

    val pipeline = Pipeline(`package`)
    pipeline += RandomChecker(jarLoader, target)
    pipeline += LoopSimplifier
    pipeline += LoopDeroller
    pipeline += PredicateStateAnalysis
    pipeline += MethodChecker(jarLoader)

    pipeline.run()
}