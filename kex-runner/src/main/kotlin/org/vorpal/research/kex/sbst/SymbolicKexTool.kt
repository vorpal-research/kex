package org.vorpal.research.kex.sbst

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.InstructionSymbolicChecker
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.config.FileConfig
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.launcher.LauncherException
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.logging.log
import java.io.File
import java.net.URLClassLoader
import kotlin.time.ExperimentalTime

@ExperimentalTime
@DelicateCoroutinesApi
@ExperimentalSerializationApi
@InternalSerializationApi
class SymbolicKexTool : Tool {
    private val configFile = "kex.ini"
    private lateinit var containers: List<Container>
    private lateinit var containerClassLoader: URLClassLoader
    lateinit var context: ExecutionContext

    init {
        kexConfig.initialize(RuntimeConfig, FileConfig(configFile))
        val logName = kexConfig.getStringValue("kex", "log", "kex.log")
        kexConfig.initLog(logName)
    }

    override fun getExtraClassPath(): List<File> = emptyList()

    private fun prepareInstrumentedClasspath(containers: List<Container>, target: Package) {
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
                containerClassLoader,
                EasyRandomDriver(),
                klassPath
            )

            executePipeline(cm, target) {
                +ClassInstantiationDetector(context)
            }
        }
        log.debug("Executed instrumentation pipeline")
    }


    override fun initialize(src: File, bin: File, classPath: List<File>) {
        val containerPaths = classPath.map { it.toPath().toAbsolutePath() }
        containerClassLoader = URLClassLoader(containerPaths.map { it.toUri().toURL() }.toTypedArray())
        containers = listOfNotNull(*containerPaths.map {
            it.asContainer() ?: throw LauncherException("Can't represent ${it.toAbsolutePath()} as class container")
        }.toTypedArray(), getKexRuntime())
        val analysisJars = listOfNotNull(*containers.toTypedArray(), getRuntime(), getIntrinsics())

        prepareInstrumentedClasspath(analysisJars, Package.defaultPackage)

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

        val accessLevel = AccessModifier.Private
        log.debug("Access level: {}", accessLevel)


        val klassPath = containers.map { it.path }
        val randomDriver = EasyRandomDriver()
        context = ExecutionContext(cm, containerClassLoader, randomDriver, klassPath, accessLevel)

        log.debug("Running with class path:\n${containers.joinToString("\n") { it.name }}")
    }

    override fun run(className: String, timeBudget: Long) {
        RuntimeConfig.setValue("symbolic", "timeLimit", timeBudget)

        val canonicalName = className.replace('.', '/')
        val klass = context.cm[canonicalName]
        log.debug("Running on klass {}", klass)
        try {
            InstructionSymbolicChecker.run(context, klass.allMethods)
        } catch (e: Throwable) {
            log.error("Error: ", e)
        }

        log.debug("Analyzed klass {}", klass)
    }

    override fun finalize() {
    }
}
