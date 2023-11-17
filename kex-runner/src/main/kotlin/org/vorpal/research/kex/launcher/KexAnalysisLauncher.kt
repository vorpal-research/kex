package org.vorpal.research.kex.launcher

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.util.PathClassLoader
import org.vorpal.research.kex.util.getIntrinsics
import org.vorpal.research.kex.util.getKexRuntime
import org.vorpal.research.kex.util.getRuntime
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.logging.log
import ru.spbstu.wheels.mapToArray
import java.nio.file.Paths


class LauncherException(message: String) : KtException(message)

internal fun prepareInstrumentedClasspath(
    containers: List<Container>,
    classLoader: ClassLoader,
    target: Package,
    prepareClassPath: (ExecutionContext) -> Pipeline.() -> Unit
) {
    val klassPath = containers.map { it.path }
    val random = EasyRandomDriver()
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
        val context = ExecutionContext(cm, classLoader, random, klassPath)

        executePipeline(cm, target) {
            +ClassInstantiationDetector(context)
            val initializerBody = prepareClassPath(context)
            initializerBody()
        }
    }
}

internal fun runPipeline(context: ExecutionContext, analysisLevel: AnalysisLevel, init: Pipeline.() -> Unit) =
    when (analysisLevel) {
        is MethodLevel -> executePipeline(context.cm, setOf(analysisLevel.method), init)
        is ClassLevel -> executePipeline(context.cm, analysisLevel.klass, init)
        is PackageLevel -> executePipeline(context.cm, analysisLevel.pkg, init)
    }


abstract class KexAnalysisLauncher(classPaths: List<String>, targetName: String) : KexLauncher {
    val containers: List<Container>
    val context: ExecutionContext
    val analysisLevel: AnalysisLevel
    val accessLevel: AccessModifier

    init {
        val containerPaths = classPaths.map { Paths.get(it).toAbsolutePath() }
        val containerClassLoader = PathClassLoader(containerPaths)
        containers = listOfNotNull(
            *containerPaths.mapToArray {
                it.asContainer() ?: throw LauncherException("Can't represent ${it.toAbsolutePath()} as class container")
            },
            getKexRuntime()
        )
        val analysisJars = listOfNotNull(*containers.toTypedArray(), getRuntime(), getIntrinsics())

        prepareInstrumentedClasspath(
            analysisJars,
            containerClassLoader,
            Package.defaultPackage,
            ::prepareClassPath
        )

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

        analysisLevel = AnalysisLevel.parse(cm, targetName)
        log.debug("Target: {}", analysisLevel)
        accessLevel = analysisLevel.accessLevel
        log.debug("Access level: {}", accessLevel)

        val klassPath = containers.map { it.path }
        val randomDriver = EasyRandomDriver()
        context = ExecutionContext(cm, containerClassLoader, randomDriver, klassPath, accessLevel)

        log.debug("Running with class path:\n${containers.joinToString("\n") { it.name }}")
    }

    protected abstract fun prepareClassPath(ctx: ExecutionContext): Pipeline.() -> Unit
}
