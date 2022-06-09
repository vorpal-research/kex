package org.vorpal.research.kex.sbst

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.InstructionConcolicChecker
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.asm.transform.SymbolicTraceCollector
import org.vorpal.research.kex.asm.transform.SystemExitTransformer
import org.vorpal.research.kex.asm.util.ClassWriter
import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.config.FileConfig
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.launcher.LauncherException
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.trace.runner.ExecutorMasterController
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path

@ExperimentalSerializationApi
@InternalSerializationApi
class KexTool : Tool {
    val configFile = "kex.ini"
    val classPath = System.getProperty("java.class.path")
    lateinit var containers: List<Container>
    lateinit var containerClassLoader: URLClassLoader
    lateinit var context: ExecutionContext
    lateinit var visibilityLevel: Visibility

    init {
        kexConfig.initialize(RuntimeConfig, FileConfig(configFile))
        val logName = kexConfig.getStringValue("kex", "log", "kex.log")
        kexConfig.initLog(logName)
    }

    override fun getExtraClassPath(): List<File> = emptyList()

    protected fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = getPathSeparator()) { "${it.path}." }
        System.setProperty("java.class.path", "$classPath${getPathSeparator()}$urlClassPath")
    }

    protected fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }

    private fun prepareInstrumentedClasspath(containers: List<Container>, target: Package, path: Path) {
        val klassPath = containers.map { it.path }
        for (jar in containers) {
            log.info("Preparing ${jar.path}")
            val cm = ClassManager(
                KfgConfig(
                    flags = Flags.readAll,
                    useCachingLoopManager = false,
                    failOnError = false,
                    verifyIR = false,
                    checkClasses = false
                )
            )
            cm.initialize(jar)
            val context = ExecutionContext(
                cm,
                target,
                containerClassLoader,
                EasyRandomDriver(),
                klassPath
            )

            jar.unpack(cm, path, true)

            executePipeline(cm, target) {
                +SystemExitTransformer(cm)
                +ClassInstantiationDetector(cm, visibilityLevel)
                +SymbolicTraceCollector(context)
                +ClassWriter(context, path)
            }
        }
        log.debug("Executed instrumentation pipeline")
    }


    override fun initialize(src: File, bin: File, classPath: List<File>) {
        visibilityLevel = kexConfig.getEnumValue("testGen", "visibility", true, Visibility.PUBLIC)
        val containerPaths = classPath.map { it.toPath().toAbsolutePath() }
        containerClassLoader = URLClassLoader(containerPaths.map { it.toUri().toURL() }.toTypedArray())
        containers = listOfNotNull(*containerPaths.map {
            it.asContainer() ?: throw LauncherException("Can't represent ${it.toAbsolutePath()} as class container")
        }.toTypedArray(), getKexRuntime())
        val analysisJars = listOfNotNull(*containers.toTypedArray(), getRuntime(), getIntrinsics())

        val instrumentedDirName = kexConfig.getStringValue("output", "instrumentedDir", "instrumented")
        val instrumentedCodeDir = kexConfig.getPathValue("kex", "outputDir")!!.resolve(instrumentedDirName)
        prepareInstrumentedClasspath(analysisJars, Package.defaultPackage, instrumentedCodeDir)

        val cm = ClassManager(
            KfgConfig(
                flags = Flags.readAll,
                useCachingLoopManager = false,
                failOnError = false,
                verifyIR = false,
                checkClasses = false
            )
        )
        cm.initialize(*analysisJars.toTypedArray())

        // write all classes to output directory, so they will be seen by ClassLoader
        val classLoader = URLClassLoader(arrayOf(instrumentedCodeDir.toUri().toURL()))

        val klassPath = containers.map { it.path }
        updateClassPath(classLoader)
        val randomDriver = EasyRandomDriver()
        context = ExecutionContext(cm, Package.defaultPackage, classLoader, randomDriver, klassPath)

        log.debug("Running with class path:\n${containers.joinToString("\n") { it.name }}")
        log.debug("Executed analysis pipeline")
    }

    override fun run(className: String, timeBudget: Long) {
        ExecutorMasterController.use {
            it.start(context)

            RuntimeConfig.setValue("concolic", "timeLimit", timeBudget)

            val canonicalName = className.replace('.', '/')
            val klass = context.cm[canonicalName]
            log.debug("Running on klass $klass")

            executePipeline(context.cm, Package.defaultPackage) {
                +SystemExitTransformer(context.cm)
            }

            InstructionConcolicChecker.run(context, klass.allMethods)

            log.debug("Analyzed klass $klass")
        }
    }

    override fun finalize() {
        clearClassPath()
        val instrumentedDirName = kexConfig.getStringValue("output", "instrumentedDir", "instrumented")
        val instrumentedCodeDir = kexConfig.getPathValue("kex", "outputDir")!!.resolve(instrumentedDirName).toAbsolutePath()
        tryOrNull { deleteDirectory(instrumentedCodeDir) }
    }
}