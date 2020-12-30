package org.jetbrains.research.kex.sbst

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.analysis.DescriptorChecker
import org.jetbrains.research.kex.asm.analysis.RandomChecker
import org.jetbrains.research.kex.asm.manager.CoverageCounter
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.RuntimeTraceCollector
import org.jetbrains.research.kex.asm.transform.SystemExitTransformer
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.reanimator.collector.ExternalConstructorCollector
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kex.reanimator.descriptor.DescriptorStatistics
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kex.trace.`object`.Trace
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

val Container.urls get() = (this.classLoader as? URLClassLoader)?.urLs ?: arrayOf()

class KexTool : Tool {
    val configFile = "kex.ini"
    val containers = listOfNotNull<Container>(getRuntime()).toMutableList()

    val classManager: ClassManager
    val origManager: ClassManager
    val outputDir: Path
    val packages = mutableListOf<Package>()
    val classPath = System.getProperty("java.class.path")
    val traceManager = ObjectTraceManager()
    lateinit var analysisContext: ExecutionContext
    lateinit var originalContext: ExecutionContext
    lateinit var cm: CoverageCounter<Trace>
    lateinit var psa: PredicateStateAnalysis


    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        System.setProperty("java.class.path", "$classPath:$urlClassPath")
    }

    private fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }

    init {
        kexConfig.initialize(RuntimeConfig, FileConfig(configFile))
        classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        outputDir = Paths.get("./temp/data/", "kex-instrumented").toAbsolutePath()
    }

    override fun getExtraClassPath(): List<File> = emptyList()

    override fun initialize(src: File, bin: File, classPath: List<File>) {
        for (container in classPath.mapNotNull { it.asContainer() }) {
            packages += container.pkg
            containers += container
        }
        log.debug("Initialized containers: ${containers.joinToString { it.name }}")
        classManager.initialize(*containers.toTypedArray())
        origManager.initialize(*containers.toTypedArray())
        log.debug("Initialized class managers")

        // write all classes to output directory, so they will be seen by ClassLoader
        containers.forEach { it.unpack(classManager, outputDir, true) }
        log.debug("Unpacked jar files")

        val classLoader = URLClassLoader(arrayOf(outputDir.toUri().toURL()))
        val jarClassLoader = URLClassLoader(containers.flatMap { it.urls.toList() }.toTypedArray())

        originalContext = ExecutionContext(origManager, jarClassLoader, EasyRandomDriver())
        analysisContext = ExecutionContext(classManager, classLoader, EasyRandomDriver())

        psa = PredicateStateAnalysis(analysisContext.cm)
        cm = CoverageCounter(originalContext.cm, traceManager)

        // instrument all classes in the target package
        executePipeline(originalContext.cm, packages) {
            +SystemExitTransformer(originalContext.cm)
            +RuntimeTraceCollector(originalContext.cm)
            +ClassWriter(originalContext, outputDir)
        }
        log.debug("Executed instrumentation pipeline")

        updateClassPath(analysisContext.loader as URLClassLoader)

        executePipeline(analysisContext.cm, packages) {
            +RandomChecker(analysisContext, traceManager)
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodFieldAccessCollector(analysisContext, psa)
            +SetterCollector(analysisContext)
            +ExternalConstructorCollector(analysisContext.cm)
        }
        log.debug("Executed analysis pipeline")
    }

    override fun run(className: String) {
        val klass = analysisContext.cm[className]
        log.debug("Running on klass $klass")
        executePipeline(analysisContext.cm, klass) {
            +DescriptorChecker(analysisContext, traceManager, psa)
        }
        log.debug("Analyzed klass $klass")
    }

    override fun finalize() {
        clearClassPath()
        val coverage = cm.totalCoverage
        log.info("Overall summary for ${cm.methodInfos.size} methods:\n" +
                "body coverage: ${String.format(Locale.ENGLISH, "%.2f", coverage.bodyCoverage)}%\n" +
                "full coverage: ${String.format(Locale.ENGLISH, "%.2f", coverage.fullCoverage)}%")
        DescriptorStatistics.printStatistics()
    }
}