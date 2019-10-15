package org.jetbrains.research.kex

import kotlinx.serialization.ImplicitReflectionSerializer
import org.jetbrains.research.kex.asm.analysis.Failure
import org.jetbrains.research.kex.asm.analysis.MethodChecker
import org.jetbrains.research.kex.asm.analysis.RandomChecker
import org.jetbrains.research.kex.asm.analysis.concolic.ConcolicChecker
import org.jetbrains.research.kex.asm.manager.CoverageCounter
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.RuntimeTraceCollector
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kex.util.debug
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

class Kex(args: Array<String>) {
    val cmd = CmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    val logName = cmd.getCmdValue("log", "kex.log")
    val config = kexConfig
    val classPath = System.getProperty("java.class.path")
    val mode = Mode.bmc

    val jar: JarFile
    val `package`: Package

    enum class Mode {
        concolic,
        bmc,
        debug
    }

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        kexConfig.initLog(logName)

        val jarName = cmd.getCmdValue("jar")
        val packageName = cmd.getCmdValue("package")
        require(jarName != null, cmd::printHelp)

        jar = JarFile(Paths.get(jarName).toAbsolutePath().toFile())
        `package` = packageName?.let { Package.parse(it) } ?: Package.defaultPackage
    }

    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        System.setProperty("java.class.path", "$classPath:$urlClassPath")
    }

    private fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }

    @ImplicitReflectionSerializer
    fun main() {
        val classManager = ClassManager(jar, `package`, Flags.readAll)
        val origManager = ClassManager(jar, `package`, Flags.readAll)

        log.debug("Running with jar ${jar.name} and package $`package`")
        val target = File("instrumented/")
        val classLoader = URLClassLoader(arrayOf(target.toURI().toURL()))
        // write all classes to target, so they will be seen by ClassLoader
        writeClassesToTarget(classManager, jar, target, `package`, true)

        val originalContext = ExecutionContext(origManager, jar.classLoader)
        val analysisContext = ExecutionContext(classManager, classLoader)

        executePipeline(originalContext.cm, `package`) {
            +RuntimeTraceCollector(originalContext.cm)
            +ClassWriter(originalContext, target)
        }

        when (cmd.getEnumValue<Mode>("mode") ?: this.mode) {
            Mode.bmc -> bmc(originalContext, analysisContext)
            Mode.concolic -> concolic(originalContext, analysisContext)
            else -> debug(analysisContext)
        }
    }

    @ImplicitReflectionSerializer
    fun debug(analysisContext: ExecutionContext) {
        val psa = PredicateStateAnalysis(analysisContext.cm)

        val psFile = cmd.getCmdValue("ps") ?: throw IllegalArgumentException("Specify PS file to debug")
        val failure = KexSerializer(analysisContext.cm).fromJson<Failure>(File(psFile).readText())

        val method = failure.method
        log.debug(failure)
        val classLoader = jar.classLoader
        updateClassPath(classLoader)

        val checker = Checker(method, classLoader, psa)
        val result = checker.check(failure.state) as? Result.SatResult ?: return
        log.debug(result.model)
        val recMod = executeModel(analysisContext, checker.state, method, result.model)
        log.debug(recMod)
        clearClassPath()
    }

    private fun bmc(originalContext: ExecutionContext, analysisContext: ExecutionContext) {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(analysisContext.cm)
        val cm = CoverageCounter(originalContext.cm, traceManager)

        updateClassPath(analysisContext.loader as URLClassLoader)
        executePipeline(analysisContext.cm, `package`) {
            +RandomChecker(analysisContext, traceManager)
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodChecker(analysisContext, traceManager, psa)
            +cm
        }
        clearClassPath()

        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format("%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format("%.2f", coverage.fullCoverage)}%")
    }


    private fun concolic(originalContext: ExecutionContext, analysisContext: ExecutionContext) {
        val traceManager = ObjectTraceManager()
        val cm = CoverageCounter(originalContext.cm, traceManager)

        executePipeline(analysisContext.cm, `package`) {
            +ConcolicChecker(analysisContext, traceManager)
            +cm
        }

        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format("%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format("%.2f", coverage.fullCoverage)}%")
    }
}