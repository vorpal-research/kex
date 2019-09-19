package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.analysis.MethodChecker
import org.jetbrains.research.kex.asm.analysis.RandomChecker
import org.jetbrains.research.kex.asm.manager.CoverageCounter
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.RuntimeTraceCollector
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.classLoader
import org.jetbrains.research.kfg.util.writeClassesToTarget
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val cmd = CmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    val logName = cmd.getCmdValue("log", "kex.log")
    kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
    kexConfig.initLog(logName)

    val jarName = cmd.getCmdValue("jar")
    val packageName = cmd.getCmdValue("package")
    require(jarName != null, cmd::printHelp)

    val jar = JarFile(Paths.get(jarName).toAbsolutePath().toFile())
    val `package` = packageName?.let { Package.parse(it) } ?: Package.defaultPackage
    val classManager = ClassManager(jar, `package`, Flags.readAll)
    val origManager = ClassManager(jar, `package`, Flags.readAll)

    log.debug("Running with jar ${jar.name} and package $`package`")
    val target = File("instrumented/")
    val classLoader = URLClassLoader(arrayOf(target.toURI().toURL()))

    val traceManager = ObjectTraceManager()
    val psa = PredicateStateAnalysis(classManager)
    val cm = CoverageCounter(origManager, traceManager)
    // write all classes to target, so they will be seen by ClassLoader
    writeClassesToTarget(classManager, jar, target, `package`, true)
    executePipeline(origManager, `package`) {
        +RuntimeTraceCollector(origManager)
        +ClassWriter(origManager, jar.classLoader, target)
    }

    executePipeline(classManager, `package`) {
        +RandomChecker(origManager, classLoader, traceManager)
        +LoopSimplifier(classManager)
        +LoopDeroller(classManager)
        +psa
        +MethodChecker(classManager, classLoader, traceManager, psa)
        +cm
    }

    val coverage = cm.totalCoverage
    log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
            "body coverage: ${String.format("%.2f", coverage.bodyCoverage)}%\n" +
            "full coverage: ${String.format("%.2f", coverage.fullCoverage)}%")
}