package org.vorpal.research.kex.launcher

import kotlinx.coroutines.DelicateCoroutinesApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.crash.CrashReproductionChecker
import org.vorpal.research.kex.asm.analysis.crash.StackTrace
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.util.PathClassLoader
import org.vorpal.research.kex.util.getIntrinsics
import org.vorpal.research.kex.util.getKexRuntime
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.util.getRuntime
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.logging.log
import ru.spbstu.wheels.mapToArray
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.ExperimentalTime

@DelicateCoroutinesApi
@ExperimentalTime
class CrashReproductionLauncher(
    classPaths: List<String>,
    crashPath: String,
    val depth: UInt
) : KexLauncher {
    private val classPath: String = System.getProperty("java.class.path")

    private val containers: List<Container>
    private val context: ExecutionContext
    private val accessLevel: AccessModifier
    private val stackTrace: StackTrace

    init {
        val containerPaths = classPaths.map { Paths.get(it).toAbsolutePath() }
        val containerClassLoader = PathClassLoader(containerPaths)
        containers = listOfNotNull(
            *containerPaths
                .filter { it.exists() }
                .mapToArray {
                    it.asContainer()
                        ?: throw LauncherException("Can't represent ${it.toAbsolutePath()} as class container")
                },
            getKexRuntime()
        )
        val analysisJars = listOfNotNull(*containers.toTypedArray(), getRuntime(), getIntrinsics())

        val cm = ClassManager(
            KfgConfig(
                flags = Flags.readAll,
                useCachingLoopManager = false,
                failOnError = false,
                verifyIR = false,
                checkClasses = false
            )
        )
        cm.initialize(analysisJars)

        accessLevel = when (kexConfig.getEnumValue("testGen", "accessLevel", true, Visibility.PUBLIC)) {
            Visibility.PRIVATE -> AccessModifier.Private
            Visibility.PUBLIC -> AccessModifier.Public
            else -> throw LauncherException("Crash reproduction supports only private or public visibility")
        }
        log.debug("Access level: {}", accessLevel)

        val klassPath = containers.map { it.path }
        updateClassPath(containerClassLoader)
        val randomDriver = EasyRandomDriver()
        context = ExecutionContext(cm, containerClassLoader, randomDriver, klassPath, accessLevel)

        log.debug("Running with class path:\n${containers.joinToString("\n") { it.name }}")
        stackTrace = StackTrace.parse(Paths.get(crashPath).readText()).let {
            when (depth) {
                0U -> it
                else -> StackTrace(it.firstLine, it.stackTraceLines.take(depth.toInt()))
            }
        }
        log.debug("Running on stack trace:\n${stackTrace.originalStackTrace}")
    }

    private fun updateClassPath(loader: PathClassLoader) {
        val urlClassPath = loader.paths.joinToString(separator = getPathSeparator()) { "${it.toAbsolutePath()}" }
        System.setProperty("java.class.path", "$classPath${getPathSeparator()}$urlClassPath")
    }

    override fun launch() {
        executePipeline(context.cm, Package.defaultPackage) {
            +ClassInstantiationDetector(context, context.accessLevel)
        }
        val testCases = CrashReproductionChecker.runWithDescriptorPreconditions(context, stackTrace)
        if (testCases.isNotEmpty()) {
            log.info("Reproducing test cases:\n${testCases.joinToString("\n")}")
        }
    }
}
