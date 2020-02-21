package org.jetbrains.research.kex

import com.abdullin.kthelper.logging.debug
import com.abdullin.kthelper.logging.log
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
import org.jetbrains.research.kex.generator.MethodFieldAccessDetector
import org.jetbrains.research.kex.generator.SetterDetector
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Jar
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

class Kex(args: Array<String>) {
    val cmd = CmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    val logName = cmd.getCmdValue("log", "kex.log")
    val config = kexConfig
    val classPath = System.getProperty("java.class.path")
    val mode = Mode.bmc

    val jar: Jar
    val outputDir: Path

    val classManager: ClassManager
    val origManager: ClassManager

    val `package`: Package
    var klass: Class? = null
    var methods: Set<Method>? = null

    enum class Mode {
        concolic,
        bmc,
        debug
    }

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        kexConfig.initLog(logName)

        val jarName = cmd.getCmdValue("jar")
        val targetName = cmd.getCmdValue("target")
        require(jarName != null, cmd::printHelp)

        val jarPath = Paths.get(jarName).toAbsolutePath()

        when {
            targetName == null -> {
                `package` = Package.defaultPackage
                jar = Jar(jarPath, `package`)
                classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                log.debug("Running with jar ${jar.name} and default package $`package`")
            }
            targetName.matches(Regex("[a-zA-Z]+(\\.[a-zA-Z]+)*\\.\\*")) -> {
                `package` = Package.parse(targetName)
                jar = Jar(jarPath, `package`)
                classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                log.debug("Running with jar ${jar.name} and package $`package`")
            }
            targetName.matches(Regex("[a-zA-Z]+(\\.[a-zA-Z]+)*\\.[a-zA-Z\$_]+::[a-zA-Z\$_]+")) -> {
                val (klassName, methodName) = targetName.split("::")
                `package` = Package.parse("${klassName.dropLastWhile { it != '.' }}*")
                jar = Jar(jarPath, `package`)
                classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                klass = classManager[klassName.replace('.', '/')]
                methods = klass!!.getMethods(methodName)
                log.debug("Running with jar ${jar.name} and methods $methods")
            }
            targetName.matches(Regex("[a-zA-Z]+(\\.[a-zA-Z]+)*\\.[a-zA-Z\$_]+")) -> {
                `package` = Package.parse("${targetName.dropLastWhile { it != '.' }}*")
                jar = Jar(jarPath, `package`)
                classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
                klass = classManager[targetName.replace('.', '/')]
                log.debug("Running with jar ${jar.name} and class $klass")
            }
            else -> {
                log.error("Could not parse target $targetName")
                cmd.printHelp()
                exitProcess(1)
            }
        }
        classManager.initialize(jar)
        origManager.initialize(jar)

        outputDir = (cmd.getCmdValue("output")?.let { Paths.get(it) }
                ?: Files.createTempDirectory(Paths.get("."), "kex-instrumented")).toAbsolutePath()
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
        // write all classes to output directory, so they will be seen by ClassLoader
        jar.unpack(classManager, outputDir, true)
        val classLoader = URLClassLoader(arrayOf(outputDir.toUri().toURL()))

        val originalContext = ExecutionContext(origManager, jar.classLoader, EasyRandomDriver())
        val analysisContext = ExecutionContext(classManager, classLoader, EasyRandomDriver())

        // instrument all classes in the target package
        runPipeline(originalContext, `package`) {
            +RuntimeTraceCollector(originalContext.cm)
            +ClassWriter(originalContext, outputDir)
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
        val result = checker.prepareAndCheck(failure.state) as? Result.SatResult ?: return
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
        runPipeline(analysisContext) {
            +RandomChecker(analysisContext, traceManager)
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodFieldAccessDetector(analysisContext, psa)
            +SetterDetector(analysisContext)
            +MethodChecker(analysisContext, traceManager, psa)
            +cm
        }
        clearClassPath()

        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format("%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format("%.2f", coverage.fullCoverage)}%")
    }

    protected fun runPipeline(context: ExecutionContext, target: Package, init: Pipeline.() -> Unit) =
            executePipeline(context.cm, target, init)

    protected fun runPipeline(context: ExecutionContext, init: Pipeline.() -> Unit) = when {
        methods != null -> executePipeline(context.cm, methods!!, init)
        klass != null -> executePipeline(context.cm, klass!!, init)
        else -> executePipeline(context.cm, `package`, init)
    }


    private fun concolic(originalContext: ExecutionContext, analysisContext: ExecutionContext) {
        val traceManager = ObjectTraceManager()
        val cm = CoverageCounter(originalContext.cm, traceManager)

        runPipeline(analysisContext) {
            +ConcolicChecker(analysisContext, traceManager)
            +cm
        }

        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format("%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format("%.2f", coverage.fullCoverage)}%")
    }
}