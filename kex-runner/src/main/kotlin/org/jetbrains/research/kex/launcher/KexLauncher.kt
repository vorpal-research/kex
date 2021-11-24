package org.jetbrains.research.kex.launcher

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.manager.ClassInstantiationDetector
import org.jetbrains.research.kex.asm.manager.CoverageCounter
import org.jetbrains.research.kex.asm.manager.MethodWrapperInitializer
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.BranchAdapter
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.RuntimeTraceCollector
import org.jetbrains.research.kex.asm.transform.SystemExitTransformer
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kex.trace.AbstractTrace
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.util.getIntrinsics
import org.jetbrains.research.kex.util.getKexRuntime
import org.jetbrains.research.kex.util.getPathSeparator
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.logging.log
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

abstract class KexLauncher(classPaths: List<String>, targetName: String) {
    val classPath = System.getProperty("java.class.path")

    val containers: List<Container>
    val containerClassLoader: URLClassLoader
    val context: ExecutionContext

    val pkg: Package
    var klass: Class? = null
        private set
    var methods: Set<Method>? = null
        private set

    val visibilityLevel: Visibility

    sealed class AnalysisLevel {
        object PACKAGE : AnalysisLevel()
        data class CLASS(val klass: String) : AnalysisLevel()
        data class METHOD(val klass: String, val method: String) : AnalysisLevel()
    }

    init {
        val containerPaths = classPaths.map { Paths.get(it).toAbsolutePath() }
        containerClassLoader = URLClassLoader(containerPaths.map { it.toUri().toURL() }.toTypedArray())

        val analysisLevel = when {
            targetName.matches(Regex("[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*\\.\\*")) -> {
                pkg = Package.parse(targetName)
                AnalysisLevel.PACKAGE
            }
            targetName.matches(Regex("([a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*\\.)?[a-zA-Z0-9\$_]+::[a-zA-Z0-9\$_]+")) -> {
                val (klassName, methodName) = targetName.split("::")
                pkg = Package.parse("${klassName.dropLastWhile { it != '.' }}*")
                AnalysisLevel.METHOD(klassName.replace('.', '/'), methodName)
            }
            targetName.matches(Regex("[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*")) -> {
                pkg = Package.parse("${targetName.dropLastWhile { it != '.' }}*")
                AnalysisLevel.CLASS(targetName.replace('.', '/'))
            }
            else -> {
                log.error("Could not parse target $targetName")
                exitProcess(1)
            }
        }
        containers = listOfNotNull(*containerPaths.map {
            it.asContainer() ?: run {
                log.error("Can't represent ${it.toAbsolutePath()} as class container")
                exitProcess(1)
            }
        }.toTypedArray(), getKexRuntime())
        val analysisJars = listOfNotNull(*containers.toTypedArray(), getRuntime(), getIntrinsics())

        val instrumentedDirName = kexConfig.getStringValue("output", "instrumentedDir", "instrumented")
        val instrumentedCodeDir = kexConfig.getPathValue("kex", "outputDir")!!.resolve(instrumentedDirName)
        prepareInstrumentedClasspath(analysisJars, pkg, instrumentedCodeDir)

        val cm = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false, verifyIR = false))
        cm.initialize(*analysisJars.toTypedArray())

        // write all classes to output directory, so they will be seen by ClassLoader
        val classLoader = URLClassLoader(arrayOf(instrumentedCodeDir.toUri().toURL()))

        val klassPath = containers.map { it.path }
        updateClassPath(classLoader)
        val randomDriver = EasyRandomDriver()
        context = ExecutionContext(cm, pkg, classLoader, randomDriver, klassPath)

        log.debug("Running with class path:\n${containers.joinToString("\n") { it.name }}")
        when (analysisLevel) {
            is AnalysisLevel.PACKAGE -> {
                log.debug("Target: package $pkg")
            }
            is AnalysisLevel.CLASS -> {
                klass = cm[analysisLevel.klass]
                if (klass !is ConcreteClass) {
                    log.error("Class $klass not found is classpath, exiting")
                    exitProcess(1)
                } else {
                    log.debug("Target: class $klass")
                }
            }
            is AnalysisLevel.METHOD -> {
                klass = cm[analysisLevel.klass]
                methods = klass!!.getMethods(analysisLevel.method)
                log.debug("Target: methods $methods")
            }
        }

        visibilityLevel = kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)
    }

    abstract fun launch()

    protected open fun createInstrumenter(context: ExecutionContext): MethodVisitor = RuntimeTraceCollector(context.cm)

    private fun prepareInstrumentedClasspath(containers: List<Container>, target: Package, path: Path) {
        val cm = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false, verifyIR = false))
        cm.initialize(*containers.toTypedArray())
        val klassPath = containers.map { it.path }
        val context = ExecutionContext(
            cm,
            target,
            containerClassLoader,
            EasyRandomDriver(),
            klassPath
        )

        containers.forEach { it.unpack(cm, path, true) }

        executePipeline(cm, target) {
            +SystemExitTransformer(cm)
            +createInstrumenter(context)
            +ClassWriter(context, path)
        }
    }

    protected fun <T : AbstractTrace> createCoverageCounter(cm: ClassManager, tm: TraceManager<T>) = when {
        methods != null -> CoverageCounter(cm, tm, methods!!)
        klass != null -> CoverageCounter(cm, tm, klass!!)
        else -> CoverageCounter(cm, tm, pkg)
    }

    protected fun runPipeline(context: ExecutionContext, target: Package, init: Pipeline.() -> Unit) =
        executePipeline(context.cm, target, init)

    protected fun runPipeline(context: ExecutionContext, init: Pipeline.() -> Unit) = when {
        methods != null -> executePipeline(context.cm, methods!!, init)
        klass != null -> executePipeline(context.cm, klass!!, init)
        else -> executePipeline(context.cm, pkg, init)
    }

    protected open fun preparePackage(
        ctx: ExecutionContext,
        psa: PredicateStateAnalysis,
        pkg: Package = Package.defaultPackage
    ) = runPipeline(ctx, pkg) {
        +MethodWrapperInitializer(ctx.cm)
        +LoopSimplifier(ctx.cm)
        +LoopDeroller(ctx.cm)
        +BranchAdapter(ctx.cm)
        +psa
        +MethodFieldAccessCollector(ctx, psa)
        +SetterCollector(ctx)
        +ClassInstantiationDetector(ctx.cm, visibilityLevel)
    }

    protected fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = getPathSeparator()) { "${it.path}." }
        System.setProperty("java.class.path", "$classPath${getPathSeparator()}$urlClassPath")
    }

    protected fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }
}