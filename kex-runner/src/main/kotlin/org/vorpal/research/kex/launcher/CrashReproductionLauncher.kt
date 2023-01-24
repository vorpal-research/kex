package org.vorpal.research.kex.launcher

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kthelper.logging.log
import java.net.URLClassLoader
import java.nio.file.Paths

class CrashReproductionLauncher(
    classPaths: List<String>,
    crashPath: String
) : KexLauncher {
    private val classPath: String = System.getProperty("java.class.path")

    val containers: List<Container>
    val context: ExecutionContext
    val accessLevel: AccessModifier

    init {
        val containerPaths = classPaths.map { Paths.get(it).toAbsolutePath() }
        val containerClassLoader = URLClassLoader(containerPaths.map { it.toUri().toURL() }.toTypedArray())
        containers = listOfNotNull(*containerPaths.map {
            it.asContainer() ?: throw LauncherException("Can't represent ${it.toAbsolutePath()} as class container")
        }.toTypedArray(), getKexRuntime())
        val analysisJars = listOfNotNull(*containers.toTypedArray(), getRuntime(), getIntrinsics())

        val instrumentedCodeDir = kexConfig.instrumentedCodeDirectory
        prepareInstrumentedClasspath(
            analysisJars,
            containerClassLoader,
            Package.defaultPackage,
            instrumentedCodeDir
        ) { {} }

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

        accessLevel = when (kexConfig.getEnumValue("testGen", "accessLevel", true, Visibility.PUBLIC)) {
            Visibility.PRIVATE -> AccessModifier.Private
            Visibility.PUBLIC -> AccessModifier.Public
            else -> throw LauncherException("Crash reproduction supports only private or public visibility")
        }
        log.debug("Access level: $accessLevel")

        val classLoader = URLClassLoader(arrayOf(instrumentedCodeDir.toUri().toURL()))
        val klassPath = containers.map { it.path }
        updateClassPath(classLoader)
        val randomDriver = EasyRandomDriver()
        context = ExecutionContext(cm, classLoader, randomDriver, klassPath, accessLevel)

        log.debug("Running with class path:\n${containers.joinToString("\n") { it.name }}")
    }

    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = getPathSeparator()) { "${it.path}." }
        System.setProperty("java.class.path", "$classPath${getPathSeparator()}$urlClassPath")
    }

    override fun launch() {
        TODO("Not yet implemented")
    }
}
