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
import org.jetbrains.research.kfg.ClassManager
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
    val classManager = ClassManager(jar, `package`, Flags.readAll)
    val origManager = ClassManager(jar, `package`, Flags.readAll)

    log.debug("Running with jar ${jar.name} and package $`package`")
    val target = File("instrumented/")
    // write all classes to target, so they will be seen by ClassLoader
    JarUtils.writeClassesToTarget(classManager, jar, target, `package`, true)

    val pipeline = Pipeline(classManager, `package`)
    pipeline += RandomChecker(classManager, jarLoader, target)
    pipeline += LoopSimplifier(classManager)
    pipeline += LoopDeroller(classManager)
    val psa = PredicateStateAnalysis(classManager)
    pipeline += psa
    pipeline += MethodChecker(classManager, jarLoader, origManager, target, psa)

    pipeline.run()
}