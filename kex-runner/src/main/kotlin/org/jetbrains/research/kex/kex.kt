package org.jetbrains.research.kex

import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.asm.analysis.DescriptorChecker
import org.jetbrains.research.kex.asm.analysis.Failure
import org.jetbrains.research.kex.asm.analysis.MethodChecker
import org.jetbrains.research.kex.asm.analysis.RandomChecker
import org.jetbrains.research.kex.asm.analysis.concolic.ConcolicChecker
import org.jetbrains.research.kex.asm.manager.CoverageCounter
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.RuntimeTraceCollector
import org.jetbrains.research.kex.asm.transform.SystemExitTransformer
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.reanimator.RandomObjectReanimator
import org.jetbrains.research.kex.reanimator.collector.ExternalCtorCollector
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kex.reanimator.descriptor.DescriptorStatistics
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.asContainer
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
import java.util.*
import kotlin.system.exitProcess

class Kex(args: Array<String>) {
    private val cmd = CmdConfig(args)
    private val properties = cmd.getCmdValue("config", "kex.ini")
    private val logName = cmd.getCmdValue("log", "kex.log")
    private val classPath = System.getProperty("java.class.path")

    val containers: List<Container>
    val containerClassLoader: URLClassLoader
    val outputDir: Path

    val classManager: ClassManager
    val origManager: ClassManager

    val `package`: Package
    var klass: Class? = null
        private set
    var methods: Set<Method>? = null
        private set

    val visibilityLevel: Visibility

    enum class Mode {
        Symbolic,
        Concolic,
        Reanimator,
        Debug
    }

    private sealed class AnalysisLevel {
        object PACKAGE : AnalysisLevel()
        data class CLASS(val klass: String) : AnalysisLevel()
        data class METHOD(val klass: String, val method: String) : AnalysisLevel()
    }

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        kexConfig.initLog(logName)

        val classPaths = cmd.getCmdValue("classpath")?.split(":")
        val targetName = cmd.getCmdValue("target")
        require(classPaths != null, cmd::printHelp)

        val containerPaths = classPaths.map { Paths.get(it).toAbsolutePath() }
        containerClassLoader = URLClassLoader(*containerPaths.map { it.toUri().toURL() }.toTypedArray())

        val analysisLevel = when {
            targetName == null -> {
                `package` = Package.defaultPackage
                AnalysisLevel.PACKAGE
            }
            targetName.matches(Regex("[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*\\.\\*")) -> {
                `package` = Package.parse(targetName)
                AnalysisLevel.PACKAGE
            }
            targetName.matches(Regex("[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*\\.[a-zA-Z0-9\$_]+::[a-zA-Z0-9\$_]+")) -> {
                val (klassName, methodName) = targetName.split("::")
                `package` = Package.parse("${klassName.dropLastWhile { it != '.' }}*")
                AnalysisLevel.METHOD(klassName.replace('.', '/'), methodName)
            }
            targetName.matches(Regex("[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*\\.[a-zA-Z0-9\$_]+")) -> {
                `package` = Package.parse("${targetName.dropLastWhile { it != '.' }}*")
                AnalysisLevel.CLASS(targetName.replace('.', '/'))
            }
            else -> {
                log.error("Could not parse target $targetName")
                cmd.printHelp()
                exitProcess(1)
            }
        }
        containers = containerPaths.map {
            it.asContainer() ?: run {
                log.error("Can't represent ${it.toAbsolutePath()} as class container")
                exitProcess(1)
            }
        }
        classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        val analysisJars = listOfNotNull(*containers.toTypedArray(), getRuntime())
        classManager.initialize(*analysisJars.toTypedArray())
        origManager.initialize(*analysisJars.toTypedArray())

        log.debug("Running with class path:\n${containers.joinToString("\n") { it.name } }")
        when (analysisLevel) {
            is AnalysisLevel.PACKAGE -> {
                log.debug("Target: package $`package`")
            }
            is AnalysisLevel.CLASS -> {
                klass = classManager[analysisLevel.klass]
                log.debug("Target: class $klass")
            }
            is AnalysisLevel.METHOD -> {
                klass = classManager[analysisLevel.klass]
                methods = klass!!.getMethods(analysisLevel.method)
                log.debug("Target: methods $methods")
            }
        }

        outputDir = (cmd.getCmdValue("output")?.let { Paths.get(it) }
                ?: Files.createTempDirectory(Paths.get("."), "kex-instrumented")).toAbsolutePath()

        visibilityLevel = kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)
    }

    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        System.setProperty("java.class.path", "$classPath:$urlClassPath")
    }

    private fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    fun main() {
        // write all classes to output directory, so they will be seen by ClassLoader
        containers.forEach { it.unpack(classManager, outputDir, true) }
        val classLoader = URLClassLoader(arrayOf(outputDir.toUri().toURL()))

        val originalContext = ExecutionContext(origManager, containerClassLoader, EasyRandomDriver())
        val analysisContext = ExecutionContext(classManager, classLoader, EasyRandomDriver())

        // instrument all classes in the target package
        runPipeline(originalContext, `package`) {
            +SystemExitTransformer(originalContext.cm)
            +RuntimeTraceCollector(originalContext.cm)
            +ClassWriter(originalContext, outputDir)
        }

        when (cmd.getEnumValue("mode", Mode.Symbolic, ignoreCase = true)) {
            Mode.Symbolic -> symbolic(originalContext, analysisContext)
            Mode.Reanimator -> reanimator(analysisContext)
            Mode.Concolic -> concolic(originalContext, analysisContext)
            Mode.Debug -> debug(analysisContext)
        }
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    fun debug(analysisContext: ExecutionContext) {
        val psa = PredicateStateAnalysis(analysisContext.cm)

        val psFile = cmd.getCmdValue("ps") ?: throw IllegalArgumentException("Specify PS file to debug")
        val failure = KexSerializer(analysisContext.cm).fromJson<Failure>(File(psFile).readText())

        val method = failure.method
        log.debug(failure)
        updateClassPath(containerClassLoader)

        val checker = Checker(method, containerClassLoader, psa)
        val result = checker.check(failure.state) as? Result.SatResult ?: return
        log.debug(result.model)
        val recMod = executeModel(analysisContext, checker.state, method, result.model)
        log.debug(recMod)
        clearClassPath()
    }

    private fun symbolic(originalContext: ExecutionContext, analysisContext: ExecutionContext) {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(analysisContext.cm)
        val cm = CoverageCounter(originalContext.cm, traceManager)

        updateClassPath(analysisContext.loader as URLClassLoader)
        val useApiGeneration = kexConfig.getBooleanValue("apiGeneration", "enabled", true)

        runPipeline(analysisContext) {
            +RandomChecker(analysisContext, traceManager)
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodFieldAccessCollector(analysisContext, psa)
            +SetterCollector(analysisContext)
            +ExternalCtorCollector(analysisContext.cm, visibilityLevel)
            +when {
                useApiGeneration -> DescriptorChecker(analysisContext, traceManager, psa)
                else -> MethodChecker(analysisContext, traceManager, psa)
            }
            +cm
        }
        clearClassPath()

        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format(Locale.ENGLISH, "%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format(Locale.ENGLISH, "%.2f", coverage.fullCoverage)}%")
        DescriptorStatistics.printStatistics()
    }

    private fun reanimator(analysisContext: ExecutionContext) {
        val psa = PredicateStateAnalysis(analysisContext.cm)

        updateClassPath(analysisContext.loader as URLClassLoader)

        runPipeline(analysisContext) {
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodFieldAccessCollector(analysisContext, psa)
            +SetterCollector(analysisContext)
            +ExternalCtorCollector(analysisContext.cm, visibilityLevel)
        }
        RandomObjectReanimator(analysisContext, `package`, psa, visibilityLevel).run()
        clearClassPath()
    }

    private fun concolic(originalContext: ExecutionContext, analysisContext: ExecutionContext) {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(analysisContext.cm)
        val cm = CoverageCounter(originalContext.cm, traceManager)

        runPipeline(analysisContext) {
            +ConcolicChecker(analysisContext, psa, traceManager)
            +cm
        }

        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format("%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format("%.2f", coverage.fullCoverage)}%")
        DescriptorStatistics.printStatistics()
    }

    private fun runPipeline(context: ExecutionContext, target: Package, init: Pipeline.() -> Unit) =
            executePipeline(context.cm, target, init)

    private fun runPipeline(context: ExecutionContext, init: Pipeline.() -> Unit) = when {
        methods != null -> executePipeline(context.cm, methods!!, init)
        klass != null -> executePipeline(context.cm, klass!!, init)
        else -> executePipeline(context.cm, `package`, init)
    }
}
